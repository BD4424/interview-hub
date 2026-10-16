package com.prephub.question;

import com.prephub.common.Enums.*;
import com.prephub.tag.Tag;
import com.prephub.topic.Topic;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_text", nullable = false)
    private String questionText;

    @Column(name = "canonical_text", nullable = false)
    private String canonicalText;

    @Column(nullable = false, unique = true, length = 64)
    private String fingerprint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QuestionType questionType;

    @Column(name = "answer_text")
    private String answerText;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_status", nullable = false)
    private AnswerStatus answerStatus = AnswerStatus.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType = SourceType.MANUAL;

    @Column(name = "source_reference")
    private String sourceReference;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    private ApprovalStatus approvalStatus = ApprovalStatus.APPROVED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany
    @JoinTable(name = "question_tags", joinColumns = @JoinColumn(name = "question_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestionCompany> companies = new ArrayList<>();

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Adds an association unless one already exists for that company. Returns true if added.
     */
    public boolean addCompany(QuestionCompany qc) {
        boolean exists = companies.stream().anyMatch(c -> c.getCompany().getId().equals(qc.getCompany().getId()));
        if (exists) return false;
        qc.setQuestion(this);
        companies.add(qc);
        return true;
    }
}
