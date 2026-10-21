package com.prephub.practice;

import com.prephub.common.Enums.Difficulty;
import com.prephub.practice.PracticeEnums.*;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "interview_turns")
@Data
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

}
