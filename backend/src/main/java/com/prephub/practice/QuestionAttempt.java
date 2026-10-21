package com.prephub.practice;

import com.prephub.practice.PracticeEnums.Confidence;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "question_attempts")
@Data
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

}
