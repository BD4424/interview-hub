package com.prephub.practice;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "question_bookmarks")
public class QuestionBookmark {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "question_id", nullable = false, unique = true) private Long questionId;
    private String notes;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long q) { this.questionId = q; }
    public String getNotes() { return notes; }
    public void setNotes(String n) { this.notes = n; }
    public Instant getCreatedAt() { return createdAt; }
}
