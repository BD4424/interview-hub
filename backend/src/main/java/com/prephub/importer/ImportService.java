package com.prephub.importer;

import com.prephub.common.BadRequestException;
import com.prephub.common.Enums;
import com.prephub.common.Enums.*;
import com.prephub.common.NotFoundException;
import com.prephub.common.TextNormalizer;
import com.prephub.company.CompanyService;
import com.prephub.importer.ImportDtos.*;
import com.prephub.question.QuestionDtos;
import com.prephub.question.QuestionDtos.CompanyAttributionRequest;
import com.prephub.question.QuestionRepository;
import com.prephub.question.QuestionWriter;
import com.prephub.tag.TagService;
import com.prephub.topic.TopicService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ImportService {
    static final Set<String> SUPPORTED = Set.of("csv");

    private final ImportJobRepository jobs;
    private final ImportCandidateRepository candidates;
    private final ImportProcessor processor;
    private final QuestionRepository questions;
    private final QuestionWriter writer;
    private final CompanyService companies;
    private final TopicService topics;
    private final TagService tags;
    private final TransactionTemplate tx;

    public ImportService(ImportJobRepository jobs, ImportCandidateRepository candidates, ImportProcessor processor,
                         QuestionRepository questions, QuestionWriter writer, CompanyService companies,
                         TopicService topics, TagService tags, TransactionTemplate tx) {
        this.jobs = jobs; this.candidates = candidates; this.processor = processor; this.questions = questions;
        this.writer = writer; this.companies = companies; this.topics = topics; this.tags = tags; this.tx = tx;
    }

    // ------------------------------------------------------------------ upload

    public record Upload(String originalFilename, byte[] content) {
        public static Upload of(MultipartFile f) {
            try { return new Upload(f.getOriginalFilename(), f.getBytes()); }
            catch (IOException e) { throw new BadRequestException("Could not read uploaded file"); }
        }
    }

    /** Validates and stores the job; processing runs asynchronously (or synchronously when async=false, e.g. tests). */
    public ImportJobDto start(Upload upload, AttributionMode mode, Long defaultCompanyId, Long defaultTopicId,
                              List<String> extraTags, boolean async) {
        if (upload.content() == null || upload.content().length == 0) throw new BadRequestException("File is empty");
        String filename = safeFilename(upload.originalFilename());
        String ext = extension(filename);
        if (!SUPPORTED.contains(ext)) throw new BadRequestException("Unsupported file type '." + ext + "'. Supported now: " + SUPPORTED);
        if (looksBinary(upload.content())) throw new BadRequestException("File does not look like a text CSV");
        AttributionMode m = mode == null ? AttributionMode.NONE : mode;
        if (m == AttributionMode.SINGLE_COMPANY && defaultCompanyId == null)
            throw new BadRequestException("Select a company for single-company attribution");
        if (defaultCompanyId != null) companies.get(defaultCompanyId);
        if (defaultTopicId != null) topics.get(defaultTopicId);

        Long jobId = tx.execute(s -> {
            ImportJob j = new ImportJob();
            j.setFilename(filename);
            j.setFileHash(TextNormalizer.sha256(upload.content()));
            j.setFormat(ext);
            j.setAttributionMode(m);
            j.setDefaultCompanyId(m == AttributionMode.NONE ? null : defaultCompanyId);
            j.setDefaultTopicId(defaultTopicId);
            j.setStatus(ImportStatus.PROCESSING);
            return jobs.save(j).getId();
        });
        List<String> extra = extraTags == null ? List.of() : extraTags;
        if (async) processor.processAsync(jobId, upload.content(), extra); else processor.process(jobId, upload.content(), extra);
        return get(jobId);
    }

    static String safeFilename(String name) {
        if (name == null || name.isBlank()) return "upload.csv";
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        if (base.length() > 200) base = base.substring(base.length() - 200);
        return base.isBlank() ? "upload.csv" : base;
    }

    static String extension(String filename) {
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    static boolean looksBinary(byte[] b) {
        int n = Math.min(b.length, 4096);
        for (int i = 0; i < n; i++) if (b[i] == 0) return true;
        return false;
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public List<ImportJobDto> list() { return jobs.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList(); }

    @Transactional(readOnly = true)
    public ImportJobDto get(Long id) { return toDto(job(id)); }

    private ImportJob job(Long id) { return jobs.findById(id).orElseThrow(() -> new NotFoundException("Import job", id)); }

    private ImportJobDto toDto(ImportJob j) {
        String defaultCompany = j.getDefaultCompanyId() == null ? null : companies.get(j.getDefaultCompanyId()).getName();
        List<ImportCandidate> cs = candidates.findByJobIdOrderByIdAsc(j.getId());
        return new ImportJobDto(j.getId(), j.getFilename(), j.getFormat(), j.getStatus(), j.getAttributionMode(),
                j.getDefaultCompanyId(), defaultCompany, j.getDefaultTopicId(),
                CompanyAttributionResolver.describe(j.getAttributionMode(), defaultCompany),
                j.getCreatedAt(), j.getCompletedAt(), j.getErrorMessage(),
                jobs.findByFileHashAndIdNot(j.getFileHash(), j.getId()).stream().map(ImportJob::getId).toList(),
                report(cs));
    }

    private ImportReport report(List<ImportCandidate> cs) {
        Map<ReviewStatus, Long> by = cs.stream().collect(Collectors.groupingBy(ImportCandidate::getReviewStatus, Collectors.counting()));
        List<ImportCandidate> valid = cs.stream().filter(c -> c.getReviewStatus() != ReviewStatus.INVALID).toList();
        Set<String> cos = new LinkedHashSet<>(), tg = new LinkedHashSet<>();
        valid.stream().filter(c -> c.getReviewStatus() != ReviewStatus.REJECTED).forEach(c -> { cos.addAll(c.companyList()); tg.addAll(c.tagList()); });
        List<String> newCos = cos.stream().filter(n -> !companies.exists(n)).toList();
        Set<String> missingTags = tags.missing(tg);
        List<String> newTags = tg.stream().filter(t -> missingTags.contains(TextNormalizer.normalizeName(t))).toList();
        return new ImportReport(cs.size(),
                n(by, ReviewStatus.IMPORTED), n(by, ReviewStatus.MERGED), n(by, ReviewStatus.AUTO_SKIPPED),
                (int) cs.stream().filter(c -> c.getMatchType() == MatchType.DUPLICATE_IN_FILE).count(),
                n(by, ReviewStatus.NEEDS_REVIEW), n(by, ReviewStatus.PENDING), n(by, ReviewStatus.INVALID), n(by, ReviewStatus.REJECTED),
                (int) valid.stream().filter(c -> c.getProposedAnswer() == null).count(),
                (int) valid.stream().filter(c -> c.companyList().isEmpty()).count(),
                newCos, newTags);
    }

    private static int n(Map<ReviewStatus, Long> m, ReviewStatus s) { return m.getOrDefault(s, 0L).intValue(); }

    static void refreshCounts(ImportJob j, List<ImportCandidate> cs) {
        j.setImportedCount((int) cs.stream().filter(c -> c.getReviewStatus() == ReviewStatus.IMPORTED).count());
        j.setDuplicateCount((int) cs.stream().filter(c -> c.getMatchType() == MatchType.EXACT_DUPLICATE || c.getMatchType() == MatchType.DUPLICATE_IN_FILE).count());
        j.setFailedCount((int) cs.stream().filter(c -> c.getReviewStatus() == ReviewStatus.INVALID).count());
        boolean open = cs.stream().anyMatch(c -> c.getReviewStatus() == ReviewStatus.PENDING || c.getReviewStatus() == ReviewStatus.NEEDS_REVIEW);
        if (j.getStatus() != ImportStatus.PROCESSING && j.getStatus() != ImportStatus.FAILED) {
            j.setStatus(open ? ImportStatus.READY_FOR_REVIEW : ImportStatus.COMPLETED);
            j.setCompletedAt(open ? null : Instant.now());
        }
    }

    @Transactional(readOnly = true)
    public List<CandidateDto> candidates(Long jobId, ReviewStatus status) {
        job(jobId);
        Long defaultTopic = job(jobId).getDefaultTopicId();
        return candidates.findByJobIdOrderByIdAsc(jobId).stream()
                .filter(c -> status == null || c.getReviewStatus() == status)
                .map(c -> toDto(c, defaultTopic)).toList();
    }

    private CandidateDto toDto(ImportCandidate c, Long defaultTopicId) {
        List<String> warnings = new ArrayList<>();
        if (c.getProposedTopic() != null && TextNormalizer.slugify(c.getProposedTopic()) != null) {
            var t = topics.resolveByNameOrFallback(c.getProposedTopic(), defaultTopicId);
            if (!Objects.equals(TextNormalizer.slugify(c.getProposedTopic()), t.getSlug()))
                warnings.add("Topic '" + c.getProposedTopic() + "' not found; will use '" + t.getName() + "'");
        }
        if (c.getProposedAnswer() == null) warnings.add("No answer");
        if (c.companyList().isEmpty()) warnings.add("Company unknown");
        QuestionDtos.QuestionDto matched = c.getMatchedQuestionId() == null ? null
                : questions.findById(c.getMatchedQuestionId()).map(QuestionDtos::toDto).orElse(null);
        return new CandidateDto(c.getId(), c.getSourceReference(), c.getRawQuestion(), c.getProposedAnswer(), c.getProposedTopic(),
                c.tagList(), c.companyList(), c.getProposedDifficulty(), c.getProposedType(), c.getInterviewRound(), c.getRole(),
                c.getJobLevel(), c.getMatchType(), c.getReviewStatus(), c.getRejectionReason(), c.getMatchedQuestionId(),
                c.getSimilarityScore(), matched, c.getCreatedQuestionId(), warnings);
    }

    // ------------------------------------------------------------------ approve / review

    /**
     * Imports the selected NEW candidates (all pending new candidates when ids is empty), then merges in-file duplicates
     * into the question their first occurrence produced. Each insert is its own transaction; a constraint race turns
     * the candidate into an exact duplicate that needs review instead of failing the batch.
     */
    public ImportJobDto approve(Long jobId, List<Long> ids) {
        ImportJob j = tx.execute(s -> job(jobId));
        if (j.getStatus() == ImportStatus.PROCESSING) throw new BadRequestException("Import is still processing");
        List<ImportCandidate> all = tx.execute(s -> candidates.findByJobIdOrderByIdAsc(jobId));
        Set<Long> selected = ids == null || ids.isEmpty() ? null : new HashSet<>(ids);
        for (ImportCandidate c : all) {
            if (c.getMatchType() != MatchType.NEW_CANDIDATE || c.getReviewStatus() != ReviewStatus.PENDING) continue;
            if (selected != null && !selected.contains(c.getId())) continue;
            importAsNew(j, c);
        }
        for (ImportCandidate c : all) {
            if (c.getMatchType() != MatchType.DUPLICATE_IN_FILE || c.getReviewStatus() != ReviewStatus.PENDING) continue;
            if (selected != null && !selected.contains(c.getId()) && !primarySelected(all, c, selected)) continue;
            questions.findByFingerprint(c.getFingerprint()).ifPresent(q -> mergeCandidate(j, c, q.getId(), true));
        }
        finishCounts(jobId);
        return get(jobId);
    }

    private static boolean primarySelected(List<ImportCandidate> all, ImportCandidate dup, Set<Long> selected) {
        return all.stream().anyMatch(o -> selected.contains(o.getId()) && Objects.equals(o.getFingerprint(), dup.getFingerprint()));
    }

    private void importAsNew(ImportJob j, ImportCandidate c) {
        Long topicId = tx.execute(s -> topics.resolveByNameOrFallback(c.getProposedTopic(), j.getDefaultTopicId()).getId());
        try {
            var res = writer.insert(new QuestionWriter.NewQuestion(c.getRawQuestion(), c.getProposedAnswer(), topicId, c.tagList(),
                    Enums.parseLenient(Difficulty.class, c.getProposedDifficulty()),
                    Enums.parseLenient(QuestionType.class, c.getProposedType()), AnswerStatus.UNVERIFIED,
                    SourceType.IMPORTED, provenance(j, c), null, ApprovalStatus.APPROVED, companyRequests(j, c)));
            saveStatus(c.getId(), ReviewStatus.IMPORTED, null, res.question().getId());
        } catch (DataIntegrityViolationException race) {
            tx.executeWithoutResult(s -> {
                ImportCandidate fresh = candidates.findById(c.getId()).orElseThrow();
                processor.reclassify(fresh);
            });
        }
    }

    private List<CompanyAttributionRequest> companyRequests(ImportJob j, ImportCandidate c) {
        return c.companyList().stream().map(name -> new CompanyAttributionRequest(null, name, c.getRole(), c.getInterviewRound(),
                c.getJobLevel(), null, provenance(j, c), AttributionStatus.REPORTED, null)).toList();
    }

    private static String provenance(ImportJob j, ImportCandidate c) { return "import #" + j.getId() + " " + j.getFilename() + " " + c.getSourceReference(); }

    private void mergeCandidate(ImportJob j, ImportCandidate c, Long targetId, boolean full) {
        var r = full
                ? writer.mergeInto(targetId, c.getProposedAnswer(), c.tagList(), companyRequests(j, c), provenance(j, c), true)
                : writer.mergeInto(targetId, null, List.of(), companyRequests(j, c), provenance(j, c), true);
        String summary = "Merged into #" + targetId + ": +" + r.companiesAdded() + " companies, +" + r.tagsAdded() + " tags"
                + (r.answerFilled() ? ", answer added" : "") + (r.alternateAnswerKept() ? ", alternate answer kept in notes" : "");
        saveStatus(c.getId(), ReviewStatus.MERGED, summary, targetId);
    }

    private void saveStatus(Long candidateId, ReviewStatus st, String reason, Long questionId) {
        tx.executeWithoutResult(s -> {
            ImportCandidate c = candidates.findById(candidateId).orElseThrow();
            c.setReviewStatus(st);
            if (reason != null) c.setRejectionReason(reason);
            if (questionId != null) c.setCreatedQuestionId(questionId);
        });
    }

    private void finishCounts(Long jobId) {
        tx.executeWithoutResult(s -> refreshCounts(job(jobId), candidates.findByJobIdOrderByIdAsc(jobId)));
    }

    public CandidateDto resolve(Long jobId, Long candidateId, ResolveRequest req) {
        ImportJob j = tx.execute(s -> job(jobId));
        ImportCandidate c = tx.execute(s -> candidate(jobId, candidateId));
        if (EnumSet.of(ReviewStatus.IMPORTED, ReviewStatus.MERGED).contains(c.getReviewStatus()))
            throw new BadRequestException("Candidate was already applied");
        switch (req.action()) {
            case REJECT -> saveStatus(c.getId(), ReviewStatus.REJECTED, req.reason() == null ? "Rejected during review" : req.reason(), null);
            case MERGE, ADD_COMPANY -> {
                if (c.getMatchedQuestionId() == null) throw new BadRequestException("Candidate has no matching question to merge into");
                mergeCandidate(j, c, c.getMatchedQuestionId(), req.action() == ReviewAction.MERGE);
            }
            case KEEP_SEPARATE, APPROVE -> {
                if (c.getReviewStatus() == ReviewStatus.INVALID) throw new BadRequestException("Fix validation errors first (edit the candidate)");
                if (c.getMatchType() == MatchType.EXACT_DUPLICATE || c.getMatchType() == MatchType.DUPLICATE_IN_FILE)
                    throw new BadRequestException("An exact duplicate cannot be kept as a separate question; merge, add company, or reject it");
                importAsNew(j, c);
            }
        }
        finishCounts(jobId);
        return tx.execute(s -> toDto(candidate(jobId, candidateId), j.getDefaultTopicId()));
    }

    /** Edit and retry validation. */
    @Transactional
    public CandidateDto edit(Long jobId, Long candidateId, EditCandidateRequest r) {
        ImportCandidate c = candidate(jobId, candidateId);
        if (EnumSet.of(ReviewStatus.IMPORTED, ReviewStatus.MERGED).contains(c.getReviewStatus()))
            throw new BadRequestException("Candidate was already applied");
        c.setRawQuestion(TextNormalizer.cleanDisplayKeepLines(r.question()));
        c.setProposedAnswer(r.answer() == null || r.answer().isBlank() ? null : r.answer().trim());
        c.setProposedTopic(TextNormalizer.cleanDisplay(r.topic()));
        c.setProposedTags(ImportCandidate.join(CsvQuestionParser.split(String.join(",", r.tags() == null ? List.of() : r.tags()), ",")));
        c.setProposedCompanies(ImportCandidate.join(CsvQuestionParser.split(String.join(";", r.companies() == null ? List.of() : r.companies()), ";")));
        c.setProposedDifficulty(TextNormalizer.cleanDisplay(r.difficulty()));
        c.setProposedType(TextNormalizer.cleanDisplay(r.questionType()));
        c.setInterviewRound(TextNormalizer.cleanDisplay(r.interviewRound()));
        c.setRole(TextNormalizer.cleanDisplay(r.role()));
        c.setJobLevel(TextNormalizer.cleanDisplay(r.jobLevel()));
        processor.reclassify(c);
        refreshCounts(c.getJob(), candidates.findByJobIdOrderByIdAsc(jobId));
        return toDto(c, c.getJob().getDefaultTopicId());
    }

    private ImportCandidate candidate(Long jobId, Long candidateId) {
        ImportCandidate c = candidates.findById(candidateId).orElseThrow(() -> new NotFoundException("Candidate", candidateId));
        if (!c.getJob().getId().equals(jobId)) throw new NotFoundException("Candidate", candidateId);
        return c;
    }

    /** Invalid and rejected rows in the same CSV layout so they can be fixed and re-imported. */
    @Transactional(readOnly = true)
    public byte[] failedRowsCsv(Long jobId) {
        job(jobId);
        StringBuilder sb = new StringBuilder("question,answer,topic,tags,company,difficulty,type,round,role,job_level,source_row,error\r\n");
        for (ImportCandidate c : candidates.findByJobIdOrderByIdAsc(jobId)) {
            if (c.getReviewStatus() != ReviewStatus.INVALID && c.getReviewStatus() != ReviewStatus.REJECTED) continue;
            sb.append(String.join(",", csv(c.getRawQuestion()), csv(c.getProposedAnswer()), csv(c.getProposedTopic()),
                    csv(String.join(", ", c.tagList())), csv(String.join("; ", c.companyList())), csv(c.getProposedDifficulty()),
                    csv(c.getProposedType()), csv(c.getInterviewRound()), csv(c.getRole()), csv(c.getJobLevel()),
                    csv(c.getSourceReference()), csv(c.getRejectionReason()))).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** CSV-escapes and neutralises spreadsheet formula injection. */
    static String csv(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
