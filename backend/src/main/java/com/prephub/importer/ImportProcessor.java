package com.prephub.importer;

import com.prephub.common.Enums;
import com.prephub.common.Enums.*;
import com.prephub.common.TextNormalizer;
import com.prephub.company.CompanyRepository;
import com.prephub.importer.parser.ParsedFile;
import com.prephub.importer.parser.ParserRegistry;
import com.prephub.importer.parser.TabularMapper;
import com.prephub.question.Question;
import com.prephub.question.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;

/** Parses a file (any supported format) into candidates and classifies each one. Never writes to the question bank. */
@Component
public class ImportProcessor {
    private static final Logger log = LoggerFactory.getLogger(ImportProcessor.class);
    static final int MAX_QUESTION_LENGTH = 10000;

    private final ImportJobRepository jobs;
    private final ImportCandidateRepository candidates;
    private final QuestionRepository questions;
    private final CompanyRepository companies;
    private final SemanticDuplicateDetector semantic;
    private final ParserRegistry parsers;
    private final TransactionTemplate tx;

    public ImportProcessor(ImportJobRepository jobs, ImportCandidateRepository candidates, QuestionRepository questions,
                           CompanyRepository companies, SemanticDuplicateDetector semantic, ParserRegistry parsers,
                           TransactionTemplate tx) {
        this.jobs = jobs; this.candidates = candidates; this.questions = questions; this.companies = companies;
        this.semantic = semantic; this.parsers = parsers; this.tx = tx;
    }

    @Async
    public void processAsync(Long jobId, byte[] content, List<String> extraTags, Map<String, String> mapping) {
        process(jobId, content, extraTags, mapping);
    }

    public void process(Long jobId, byte[] content, List<String> extraTags, Map<String, String> mapping) {
        try {
            tx.executeWithoutResult(s -> doProcess(jobId, content, extraTags, mapping));
        } catch (RuntimeException e) {
            log.warn("Import job {} failed: {}", jobId, e.getClass().getSimpleName());
            tx.executeWithoutResult(s -> {
                ImportJob job = jobs.findById(jobId).orElseThrow();
                job.setStatus(ImportStatus.FAILED);
                job.setErrorMessage(e instanceof IllegalArgumentException ? e.getMessage() : "Could not process file");
                job.setCompletedAt(Instant.now());
            });
        }
    }

    private void doProcess(Long jobId, byte[] content, List<String> extraTags, Map<String, String> mapping) {
        ImportJob job = jobs.findById(jobId).orElseThrow();
        String defaultCompany = job.getDefaultCompanyId() == null ? null
                : companies.findById(job.getDefaultCompanyId()).map(c -> c.getName()).orElse(null);
        ParsedFile parsed = parsers.get(job.getFormat()).parse(content, mapping == null ? Map.of() : mapping);
        List<QuestionCandidate> rows = parsed.candidates();
        job.setParseNotes(String.join("\n", parsed.notes()));

        Set<String> fps = new HashSet<>();
        rows.forEach(r -> { String f = TextNormalizer.fingerprint(r.question()); if (f != null) fps.add(f); });
        Map<String, Question> existing = new HashMap<>();
        if (!fps.isEmpty()) questions.findByFingerprintIn(fps).forEach(q -> existing.put(q.getFingerprint(), q));

        Map<String, String> firstInFile = new HashMap<>();
        for (QuestionCandidate r : rows) {
            ImportCandidate c = new ImportCandidate();
            c.setJob(job);
            c.setSourceReference(truncate(r.sourceReference(), 100));
            c.setRawQuestion(r.question());
            c.setProposedAnswer(r.answer());
            c.setProposedTopic(truncate(r.topic(), 120));
            List<String> tags = new ArrayList<>(r.tags());
            if (extraTags != null) tags.addAll(extraTags);
            c.setProposedTags(ImportCandidate.join(TabularMapper.split(String.join(",", tags), ",")));
            c.setProposedCompanies(ImportCandidate.join(CompanyAttributionResolver.resolve(job.getAttributionMode(), defaultCompany, r.companies())));
            c.setProposedDifficulty(truncate(r.difficulty(), 20));
            c.setProposedType(truncate(r.questionType(), 30));
            c.setInterviewRound(truncate(r.interviewRound(), 100));
            c.setRole(truncate(r.role(), 200));
            c.setJobLevel(truncate(r.jobLevel(), 100));
            classify(c, existing, firstInFile);
            candidates.save(c);
        }
        job.setTotalRecords(rows.size());
        job.setStatus(ImportStatus.READY_FOR_REVIEW);
        ImportService.refreshCounts(job, candidates.findByJobIdOrderByIdAsc(jobId));
    }

    private static String truncate(String s, int max) { return s == null || s.length() <= max ? s : s.substring(0, max); }

    /** Pure classification; package-visible for re-validation after edits. */
    void classify(ImportCandidate c, Map<String, Question> existing, Map<String, String> firstInFile) {
        c.setMatchedQuestionId(null); c.setSimilarityScore(null); c.setRejectionReason(null);
        String error = validate(c);
        if (error != null) {
            c.setMatchType(MatchType.INVALID); c.setReviewStatus(ReviewStatus.INVALID); c.setRejectionReason(error);
            c.setFingerprint(null);
            return;
        }
        String fp = TextNormalizer.fingerprint(c.getRawQuestion());
        c.setFingerprint(fp);
        Question match = existing.get(fp);
        if (match != null) {
            c.setMatchType(MatchType.EXACT_DUPLICATE);
            c.setMatchedQuestionId(match.getId());
            c.setSimilarityScore(1.0);
            List<String> news = newInformation(c, match);
            if (news.isEmpty()) {
                c.setReviewStatus(ReviewStatus.AUTO_SKIPPED);
                c.setRejectionReason("Identical to existing question #" + match.getId() + "; no new metadata");
            } else {
                c.setReviewStatus(ReviewStatus.NEEDS_REVIEW);
                c.setRejectionReason("Identical to question #" + match.getId() + ". New information: " + String.join("; ", news));
            }
            return;
        }
        if (firstInFile != null) {
            String first = firstInFile.putIfAbsent(fp, c.getSourceReference());
            if (first != null) {
                c.setMatchType(MatchType.DUPLICATE_IN_FILE);
                c.setReviewStatus(ReviewStatus.PENDING);
                c.setRejectionReason("Same question as " + first + "; its companies/tags/answer will be merged into that question on approval");
                return;
            }
        }
        List<SemanticDuplicateDetector.SemanticMatch> sims = semantic.findSimilar(c.getRawQuestion(), 3);
        if (!sims.isEmpty()) {
            var best = sims.get(0);
            c.setMatchedQuestionId(best.questionId());
            c.setSimilarityScore(best.score());
            c.setMatchType(best.score() >= 0.92 ? MatchType.LIKELY_SEMANTIC_DUPLICATE : MatchType.POSSIBLE_DUPLICATE);
            c.setReviewStatus(ReviewStatus.NEEDS_REVIEW);
            return;
        }
        c.setMatchType(MatchType.NEW_CANDIDATE);
        c.setReviewStatus(ReviewStatus.PENDING);
    }

    static String validate(ImportCandidate c) {
        if (c.getRawQuestion() == null || TextNormalizer.canonicalQuestion(c.getRawQuestion()) == null) return "Question text is empty";
        if (c.getRawQuestion().length() > MAX_QUESTION_LENGTH) return "Question text exceeds " + MAX_QUESTION_LENGTH + " characters";
        if (c.getProposedDifficulty() != null && Enums.parseLenient(Difficulty.class, c.getProposedDifficulty()) == null)
            return "Unknown difficulty '" + c.getProposedDifficulty() + "' (use BEGINNER, INTERMEDIATE or ADVANCED)";
        if (c.getProposedType() != null && Enums.parseLenient(QuestionType.class, c.getProposedType()) == null)
            return "Unknown question type '" + c.getProposedType() + "'";
        for (String co : c.companyList()) if (co.length() > 200) return "Company name too long: " + co.substring(0, 40) + "...";
        for (String t : c.tagList()) if (t.length() > 120) return "Tag too long: " + t.substring(0, 40) + "...";
        return null;
    }

    static List<String> newInformation(ImportCandidate c, Question q) {
        List<String> out = new ArrayList<>();
        Set<String> have = new HashSet<>();
        q.getCompanies().forEach(x -> have.add(x.getCompany().getNormalizedName()));
        List<String> newCos = c.companyList().stream().filter(n -> !have.contains(TextNormalizer.normalizeName(n))).toList();
        if (!newCos.isEmpty()) out.add("companies " + String.join(", ", newCos));
        Set<String> haveTags = new HashSet<>();
        q.getTags().forEach(t -> haveTags.add(t.getNormalizedName()));
        List<String> newTags = c.tagList().stream().filter(n -> !haveTags.contains(TextNormalizer.normalizeName(n))).toList();
        if (!newTags.isEmpty()) out.add("tags " + String.join(", ", newTags));
        if (c.getProposedAnswer() != null) {
            if (q.getAnswerText() == null) out.add("an answer (existing question has none)");
            else if (!Objects.equals(TextNormalizer.normalizeName(q.getAnswerText()), TextNormalizer.normalizeName(c.getProposedAnswer())))
                out.add("a different answer (will be kept in notes, existing answer preserved)");
        }
        return out;
    }

    @Transactional
    public void reclassify(ImportCandidate c) {
        Map<String, Question> existing = new HashMap<>();
        String fp = TextNormalizer.fingerprint(c.getRawQuestion());
        if (fp != null) questions.findByFingerprint(fp).ifPresent(q -> existing.put(fp, q));
        Map<String, String> firstInFile = new HashMap<>();
        for (ImportCandidate other : candidates.findByJobIdOrderByIdAsc(c.getJob().getId())) {
            if (other.getId().equals(c.getId()) || other.getFingerprint() == null) continue;
            if (other.getReviewStatus() == ReviewStatus.REJECTED || other.getReviewStatus() == ReviewStatus.INVALID) continue;
            if (other.getId() < c.getId()) firstInFile.putIfAbsent(other.getFingerprint(), other.getSourceReference());
        }
        classify(c, existing, firstInFile);
    }
}
