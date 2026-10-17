package com.prephub.practice;

import com.prephub.common.Enums.Difficulty;
import com.prephub.practice.PracticeEnums.*;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "interview_turns")
public class InterviewTurn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", nullable = false) private Long sessionId;
    @Column(name = "question_id") private Long questionId;                 // null for session-only follow-ups
    @Column(name = "question_text_snapshot", nullable = false) private String questionText;
    @Column(name = "user_answer") private String userAnswer;
    @Column(name = "ai_feedback") private String feedbackJson;             // serialized EvaluationValidator.Evaluation
    private Double score;
    @Column(name = "turn_number", nullable = false) private int turnNumber;
    @Enumerated(EnumType.STRING) @Column(name = "turn_type", nullable = false) private TurnType type = TurnType.BANK;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TurnStatus status = TurnStatus.ASKED;
    @Column(name = "parent_turn_id") private Long parentTurnId;
    @Column(name = "expected_points") private String expectedPoints;       // newline-separated, follow-ups only
    @Enumerated(EnumType.STRING) private Difficulty difficulty;
    @Column(name = "topic_name") private String topicName;
    @Enumerated(EnumType.STRING) private Confidence confidence;
    @Column(name = "asked_at") private Instant askedAt;
    @Column(name = "answered_at") private Instant answeredAt;

    @PrePersist void pre() { if (askedAt == null) askedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long s) { this.sessionId = s; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long q) { this.questionId = q; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String t) { this.questionText = t; }
    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String a) { this.userAnswer = a; }
    public String getFeedbackJson() { return feedbackJson; }
    public void setFeedbackJson(String f) { this.feedbackJson = f; }
    public Double getScore() { return score; }
    public void setScore(Double s) { this.score = s; }
    public int getTurnNumber() { return turnNumber; }
    public void setTurnNumber(int n) { this.turnNumber = n; }
    public TurnType getType() { return type; }
    public void setType(TurnType t) { this.type = t; }
    public TurnStatus getStatus() { return status; }
    public void setStatus(TurnStatus s) { this.status = s; }
    public Long getParentTurnId() { return parentTurnId; }
    public void setParentTurnId(Long p) { this.parentTurnId = p; }
    public String getExpectedPoints() { return expectedPoints; }
    public void setExpectedPoints(String e) { this.expectedPoints = e; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty d) { this.difficulty = d; }
    public String getTopicName() { return topicName; }
    public void setTopicName(String t) { this.topicName = t; }
    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence c) { this.confidence = c; }
    public Instant getAskedAt() { return askedAt; }
    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant a) { this.answeredAt = a; }
}
