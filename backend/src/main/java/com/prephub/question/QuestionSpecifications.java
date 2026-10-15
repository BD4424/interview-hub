package com.prephub.question;

import com.prephub.common.Enums.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filters use EXISTS sub-queries so multi-valued joins never duplicate rows or break pagination. */
public final class QuestionSpecifications {
    private QuestionSpecifications() {}

    public record Filter(String q, Long topicId, Long companyId, List<Long> tagIds, boolean matchAllTags,
                         Difficulty difficulty, QuestionType type, Boolean hasAnswer, ApprovalStatus approval) {}

    public static Specification<Question> of(Filter f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("approvalStatus"), f.approval() == null ? ApprovalStatus.APPROVED : f.approval()));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.like(root.<String>get("canonicalText"), like, '\\'));
            }
            if (f.topicId() != null) ps.add(cb.equal(root.get("topic").get("id"), f.topicId()));
            if (f.difficulty() != null) ps.add(cb.equal(root.get("difficulty"), f.difficulty()));
            if (f.type() != null) ps.add(cb.equal(root.get("questionType"), f.type()));
            if (f.hasAnswer() != null) ps.add(f.hasAnswer() ? cb.isNotNull(root.get("answerText")) : cb.isNull(root.get("answerText")));
            if (f.companyId() != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<QuestionCompany> qc = sq.from(QuestionCompany.class);
                sq.select(qc.get("id")).where(cb.equal(qc.get("question"), root), cb.equal(qc.get("company").get("id"), f.companyId()));
                ps.add(cb.exists(sq));
            }
            if (f.tagIds() != null && !f.tagIds().isEmpty()) {
                if (f.matchAllTags()) {
                    for (Long tagId : f.tagIds()) ps.add(cb.exists(tagExists(root, query, cb, List.of(tagId))));
                } else {
                    ps.add(cb.exists(tagExists(root, query, cb, f.tagIds())));
                }
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    private static Subquery<Long> tagExists(Root<Question> root, CriteriaQuery<?> query, CriteriaBuilder cb, List<Long> tagIds) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<Question> q2 = sq.from(Question.class);
        Join<Object, Object> t = q2.join("tags");
        sq.select(q2.get("id")).where(cb.equal(q2.get("id"), root.get("id")), t.get("id").in(tagIds));
        return sq;
    }
}
