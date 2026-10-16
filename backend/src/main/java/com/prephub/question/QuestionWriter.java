package com.prephub.question;

import com.prephub.common.BadRequestException;
import com.prephub.common.Enums.*;
import com.prephub.common.NotFoundException;
import com.prephub.common.TextNormalizer;
import com.prephub.company.CompanyService;
import com.prephub.question.QuestionDtos.CompanyAttributionRequest;
import com.prephub.tag.TagService;
import com.prephub.topic.TopicService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * All writes that can hit the fingerprint unique constraint run here in their own transaction, so the caller can
 * catch DataIntegrityViolationException cleanly (a failed flush poisons the surrounding Hibernate session).
 */
@Component
public class QuestionWriter {
    private final QuestionRepository repo;
    private final TopicService topics;
    private final TagService tags;
    private final CompanyService companies;

    public QuestionWriter(QuestionRepository repo, TopicService topics, TagService tags, CompanyService companies) {
        this.repo = repo; this.topics = topics; this.tags = tags; this.companies = companies;
    }

    public record NewQuestion(String questionText, String answerText, Long topicId, Collection<String> tagNames,
                              Difficulty difficulty, QuestionType questionType, AnswerStatus answerStatus,
                              SourceType sourceType, String sourceReference, String notes, ApprovalStatus approvalStatus,
                              List<CompanyAttributionRequest> companies) {}

    public record WriteResult(Question question, int newCompanies) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WriteResult insert(NewQuestion n) {
        String canonical = TextNormalizer.canonicalQuestion(n.questionText());
        if (canonical == null) throw new BadRequestException("Question text is required");
        Question q = new Question();
        q.setQuestionText(TextNormalizer.cleanDisplayKeepLines(n.questionText()));
        q.setCanonicalText(canonical);
        q.setFingerprint(TextNormalizer.sha256(canonical));
        q.setTopic(topics.get(n.topicId()));
        q.setDifficulty(n.difficulty());
        q.setQuestionType(n.questionType());
        applyAnswer(q, n.answerText(), n.answerStatus());
        q.setSourceType(n.sourceType());
        q.setSourceReference(n.sourceReference());
        q.setNotes(n.notes());
        q.setApprovalStatus(n.approvalStatus());
        q.getTags().addAll(tags.resolveOrCreate(n.tagNames()));
        int created = syncCompanies(q, n.companies(), false);
        return new WriteResult(repo.saveAndFlush(q), created);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QuestionDtos.QuestionDto update(Long id, QuestionDtos.QuestionRequest r) {
        Question q = repo.findById(id).orElseThrow(() -> new NotFoundException("Question", id));

        String canonical = TextNormalizer.canonicalQuestion(r.questionText());

        if (canonical == null) throw new BadRequestException("Question text is required");

        q.setQuestionText(TextNormalizer.cleanDisplayKeepLines(r.questionText()));
        q.setCanonicalText(canonical);
        q.setFingerprint(TextNormalizer.sha256(canonical));
        q.setTopic(topics.get(r.topicId()));
        q.setDifficulty(r.difficulty());
        q.setQuestionType(r.questionType());
        applyAnswer(q, r.answerText(), r.answerStatus());
        q.setSourceReference(r.sourceReference());
        q.setNotes(r.notes());
        q.getTags().clear();
        q.getTags().addAll(tags.resolveOrCreate(r.tags()));
        syncCompanies(q, r.companies(), true);

        repo.saveAndFlush(q);

        return QuestionDtos.toDto(q);
    }

    private static void applyAnswer(Question q, String answer, AnswerStatus requested) {
        String a = answer == null || answer.isBlank() ? null : answer.trim();
        q.setAnswerText(a);
        if (a == null) q.setAnswerStatus(AnswerStatus.NONE);
        else q.setAnswerStatus(requested == null || requested == AnswerStatus.NONE ? AnswerStatus.UNVERIFIED : requested);
    }

    /** replace=true: the request is the full desired list (edit form). replace=false: additive only. */
    int syncCompanies(Question q, List<CompanyAttributionRequest> reqs, boolean replace) {
        int created = 0;
        Set<Long> keep = new HashSet<>();
        for (CompanyAttributionRequest r : reqs == null ? List.<CompanyAttributionRequest>of() : reqs) {
            CompanyService.Resolved res;
            if (r.companyId() != null) res = new CompanyService.Resolved(companies.get(r.companyId()), false);
            else if (r.companyName() != null && !r.companyName().isBlank()) res = companies.resolveOrCreate(r.companyName());
            else continue;
            if (res.created()) created++;
            Long cid = res.company().getId();
            keep.add(cid);
            QuestionCompany qc = q.getCompanies().stream().filter(x -> x.getCompany().getId().equals(cid)).findFirst().orElse(null);
            if (qc == null) {
                qc = new QuestionCompany();
                qc.setCompany(res.company());
                q.addCompany(qc);
            } else if (!replace) {
                continue; // never overwrite existing association metadata on additive merges
            }
            qc.setRole(r.role()); qc.setInterviewRound(r.interviewRound()); qc.setJobLevel(r.jobLevel());
            qc.setDateAsked(r.dateAsked()); qc.setSourceReference(r.sourceReference()); qc.setNotes(r.notes());
            qc.setAttributionStatus(r.attributionStatus() == null ? AttributionStatus.REPORTED : r.attributionStatus());
        }
        if (replace) q.getCompanies().removeIf(x -> !keep.contains(x.getCompany().getId()));
        return created;
    }

    public record MergeResult(Long questionId, int companiesAdded, int tagsAdded, boolean answerFilled, boolean alternateAnswerKept, int newCompanies) {}

    /**
     * Merge incoming metadata into an existing canonical question without losing anything:
     * adds missing tags and company associations, fills an empty answer, and keeps a differing answer in notes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MergeResult mergeInto(Long questionId, String incomingAnswer, Collection<String> tagNames,
                                 List<CompanyAttributionRequest> companyReqs, String provenance, boolean includeCompanies) {
        Question q = repo.findById(questionId).orElseThrow(() -> new NotFoundException("Question", questionId));
        int beforeTags = q.getTags().size();
        q.getTags().addAll(tags.resolveOrCreate(tagNames));
        int tagsAdded = q.getTags().size() - beforeTags;
        int beforeCompanies = q.getCompanies().size();
        int created = includeCompanies ? syncCompanies(q, companyReqs, false) : 0;
        int companiesAdded = q.getCompanies().size() - beforeCompanies;
        boolean filled = false, kept = false;
        String a = incomingAnswer == null || incomingAnswer.isBlank() ? null : incomingAnswer.trim();
        if (a != null) {
            if (q.getAnswerText() == null) { q.setAnswerText(a); q.setAnswerStatus(AnswerStatus.UNVERIFIED); filled = true; }
            else if (!TextNormalizer.normalizeName(q.getAnswerText()).equals(TextNormalizer.normalizeName(a))) {
                String block = "Alternate answer (" + provenance + "):\n" + a;
                q.setNotes(q.getNotes() == null ? block : q.getNotes() + "\n\n" + block);
                kept = true;
            }
        }
        repo.saveAndFlush(q);
        return new MergeResult(q.getId(), companiesAdded, tagsAdded, filled, kept, created);
    }
}
