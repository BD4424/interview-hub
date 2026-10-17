package com.prephub.practice;

import com.prephub.practice.PracticeEnums.Confidence;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "question_attempts")
public class QuestionAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "question_id", nullable = false) private Long questionId;
    @Column(name = "session_id") private Long sessionId;
    @Column(name = "user_answer") private String userAnswer;
    private Double score;
    @Enumerated(EnumType.STRING) private Confidence confidence;
    private String feedback;
    @Column(name = "attempted_at", nullable = false) private Instant attemptedAt;

    @PrePersist void pre() { if (attemptedAt == null) attemptedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long q) { this.questionId = q; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long s) { this.sessionId = s; }
    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String a) { this.userAnswer = a; }
    public Double getScore() { return score; }
    public void setScore(Double s) { this.score = s; }
    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence c) { this.confidence = c; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String f) { this.feedback = f; }
    public Instant getAttemptedAt() { return attemptedAt; }
}
