package com.prephub.question;

import com.prephub.common.DuplicateQuestionException;
import com.prephub.common.Enums.*;
import com.prephub.common.NotFoundException;
import com.prephub.common.PageResponse;
import com.prephub.common.TextNormalizer;
import com.prephub.question.QuestionDtos.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class QuestionService {
    private final QuestionRepository repo;
    private final QuestionWriter writer;

    public QuestionService(QuestionRepository repo, QuestionWriter writer) { this.repo = repo; this.writer = writer; }

    /** Manual creation. Exact duplicates -> 409 with the matching canonical question (also under races). */
    public QuestionDto create(QuestionRequest r) {
        String fp = TextNormalizer.fingerprint(r.questionText());
        if (fp != null) repo.findByFingerprint(fp).ifPresent(e -> { throw new DuplicateQuestionException(e.getId(), e.getQuestionText()); });
        try {
            Question q = writer.insert(new QuestionWriter.NewQuestion(r.questionText(), r.answerText(), r.topicId(), r.tags(),
                    r.difficulty(), r.questionType(), r.answerStatus(), SourceType.MANUAL, r.sourceReference(), r.notes(),
                    ApprovalStatus.APPROVED, r.companies())).question();
            return get(q.getId());
        } catch (DataIntegrityViolationException e) {
            Question existing = repo.findByFingerprint(fp).orElseThrow(() -> e);
            throw new DuplicateQuestionException(existing.getId(), existing.getQuestionText());
        }
    }

    public QuestionDto update(Long id, QuestionRequest r) {
        String fp = TextNormalizer.fingerprint(r.questionText());

        repo.findByFingerprint(fp)
                .filter(e -> !e.getId().equals(id))
                .ifPresent(e -> {
                    throw new DuplicateQuestionException(
                            e.getId(), e.getQuestionText());
                });

        try {
            return writer.update(id, r);
        } catch (DataIntegrityViolationException e) {
            Question existing = repo.findByFingerprint(fp)
                    .orElseThrow(() -> e);

            throw new DuplicateQuestionException(
                    existing.getId(), existing.getQuestionText());
        }
    }

    @Transactional(readOnly = true)
    public QuestionDto get(Long id) {
        Question q = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Question", id));

        return QuestionDtos.toDto(q);
    }

    @Transactional(readOnly = true)
    public Optional<Question> findByFingerprint(String fp) { return repo.findByFingerprint(fp); }

    @Transactional
    public void delete(Long id) {
        repo.delete(repo.findById(id).orElseThrow(() -> new NotFoundException("Question", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionDto> search(QuestionSpecifications.Filter f, int page, int size, String sort) {
        Sort s = switch (sort == null ? "newest" : sort) {
            case "oldest" -> Sort.by("createdAt").ascending();
            case "topic" -> Sort.by("topic.name").ascending().and(Sort.by("id"));
            case "difficulty" -> Sort.by("difficulty").ascending().and(Sort.by("id"));
            default -> Sort.by("createdAt").descending().and(Sort.by("id").descending());
        };
        Page<Question> p = repo.findAll(QuestionSpecifications.of(f), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), s));
        return PageResponse.of(p, QuestionDtos::toDto);
    }

    /** Adds one company association (or none if it already exists). */
    public QuestionDto addCompany(Long questionId, CompanyAttributionRequest r) {
        writer.mergeInto(questionId, null, List.of(), List.of(r), "manual", true);
        return get(questionId);
    }

    @Transactional
    public QuestionDto removeCompany(Long questionId, Long companyId) {
        Question q = repo.findById(questionId).orElseThrow(() -> new NotFoundException("Question", questionId));
        q.getCompanies().removeIf(c -> c.getCompany().getId().equals(companyId));
        repo.flush();
        return QuestionDtos.toDto(q);
    }
}
