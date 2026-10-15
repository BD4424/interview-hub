package com.prephub.question;

import com.prephub.common.Enums.*;
import com.prephub.tag.Tag;
import com.prephub.topic.Topic;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "questions")
public class Question {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "question_text", nullable = false) private String questionText;
    @Column(name = "canonical_text", nullable = false) private String canonicalText;
    @Column(nullable = false, unique = true, length = 64) private String fingerprint;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "topic_id") private Topic topic;
    @Enumerated(EnumType.STRING) private Difficulty difficulty;
    @Enumerated(EnumType.STRING) @Column(name = "question_type") private QuestionType questionType;
    @Column(name = "answer_text") private String answerText;
    @Enumerated(EnumType.STRING) @Column(name = "answer_status", nullable = false) private AnswerStatus answerStatus = AnswerStatus.NONE;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false) private SourceType sourceType = SourceType.MANUAL;
    @Column(name = "source_reference") private String sourceReference;
    private String notes;
    @Enumerated(EnumType.STRING) @Column(name = "approval_status", nullable = false) private ApprovalStatus approvalStatus = ApprovalStatus.APPROVED;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @ManyToMany
    @JoinTable(name = "question_tags", joinColumns = @JoinColumn(name = "question_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestionCompany> companies = new ArrayList<>();

    @PrePersist void prePersist() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    /** Adds an association unless one already exists for that company. Returns true if added. */
    public boolean addCompany(QuestionCompany qc) {
        boolean exists = companies.stream().anyMatch(c -> c.getCompany().getId().equals(qc.getCompany().getId()));
        if (exists) return false;
        qc.setQuestion(this);
        companies.add(qc);
        return true;
    }

    public Long getId() { return id; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String t) { this.questionText = t; }
    public String getCanonicalText() { return canonicalText; }
    public void setCanonicalText(String c) { this.canonicalText = c; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String f) { this.fingerprint = f; }
    public Topic getTopic() { return topic; }
    public void setTopic(Topic t) { this.topic = t; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty d) { this.difficulty = d; }
    public QuestionType getQuestionType() { return questionType; }
    public void setQuestionType(QuestionType q) { this.questionType = q; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String a) { this.answerText = a; }
    public AnswerStatus getAnswerStatus() { return answerStatus; }
    public void setAnswerStatus(AnswerStatus a) { this.answerStatus = a; }
    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType s) { this.sourceType = s; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String s) { this.sourceReference = s; }
    public String getNotes() { return notes; }
    public void setNotes(String n) { this.notes = n; }
    public ApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(ApprovalStatus a) { this.approvalStatus = a; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<Tag> getTags() { return tags; }
    public List<QuestionCompany> getCompanies() { return companies; }
}
