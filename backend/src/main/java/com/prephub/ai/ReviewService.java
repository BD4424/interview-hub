package com.prephub.ai;

import com.prephub.ai.AiDtos.ApprovalRequest;
import com.prephub.ai.AiDtos.Decision;
import com.prephub.common.BadRequestException;
import com.prephub.common.Enums.ApprovalStatus;
import com.prephub.common.NotFoundException;
import com.prephub.question.Question;
import com.prephub.question.QuestionDtos;
import com.prephub.question.QuestionRepository;
import com.prephub.tag.TagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Approval of PENDING questions (AI-generated). Approve: the question joins the bank, optionally applying the
 * reviewer-selected suggested tags. Reject: the pending question is deleted (its relation rows cascade), so its
 * fingerprint never blocks a later manual or imported copy of the same text.
 */
@Service
public class ReviewService {
    private final QuestionRepository questions;
    private final TagService tags;

    public ReviewService(QuestionRepository questions, TagService tags) { this.questions = questions; this.tags = tags; }

    @Transactional
    public QuestionDtos.QuestionDto decide(Long id, ApprovalRequest r) {
        Question q = questions.findById(id).orElseThrow(() -> new NotFoundException("Question", id));
        if (q.getApprovalStatus() != ApprovalStatus.PENDING) throw new BadRequestException("Question #" + id + " is not pending review");
        if (r.decision() == Decision.REJECT) {
            questions.delete(q);
            return null;
        }
        List<String> apply = r.applyTags() == null ? List.of() : r.applyTags();
        if (!apply.isEmpty()) {
            q.getTags().addAll(tags.resolveOrCreate(apply));
            if (q.getNotes() != null)
                q.setNotes(q.getNotes().replaceAll("(?m)^" + java.util.regex.Pattern.quote(AiQuestionService.SUGGESTED_TAGS_PREFIX) + ".*$", "").trim());
        }
        q.setApprovalStatus(ApprovalStatus.APPROVED);
        questions.flush();
        return QuestionDtos.toDto(q);
    }
}
