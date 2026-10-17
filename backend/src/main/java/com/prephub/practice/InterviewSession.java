package com.prephub.practice;

import com.prephub.common.Enums.Difficulty;
import com.prephub.practice.PracticeEnums.SessionStatus;
import com.prephub.practice.PracticeEnums.SessionType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "interview_sessions")
public class InterviewSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "session_type", nullable = false) private SessionType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SessionStatus status = SessionStatus.ACTIVE;
    @Column(name = "topic_id") private Long topicId;
    @Column(name = "company_id") private Long companyId;
    @Column(name = "tag_ids") private String tagIds;
    @Enumerated(EnumType.STRING) private Difficulty difficulty;
    @Column(name = "question_limit", nullable = false) private int questionLimit;
    @Column(name = "seed_question_id") private Long seedQuestionId;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "overall_score") private Double overallScore;

    @PrePersist void pre() { if (startedAt == null) startedAt = Instant.now(); }

    public List<Long> tagIdList() {
        return tagIds == null || tagIds.isBlank() ? List.of() : Arrays.stream(tagIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
    }
    public void setTagIdList(List<Long> ids) {
        this.tagIds = ids == null || ids.isEmpty() ? null : ids.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    public Long getId() { return id; }
    public SessionType getType() { return type; }
    public void setType(SessionType t) { this.type = t; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus s) { this.status = s; }
    public Long getTopicId() { return topicId; }
    public void setTopicId(Long t) { this.topicId = t; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long c) { this.companyId = c; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty d) { this.difficulty = d; }
    public int getQuestionLimit() { return questionLimit; }
    public void setQuestionLimit(int l) { this.questionLimit = l; }
    public Long getSeedQuestionId() { return seedQuestionId; }
    public void setSeedQuestionId(Long s) { this.seedQuestionId = s; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant c) { this.completedAt = c; }
    public Double getOverallScore() { return overallScore; }
    public void setOverallScore(Double s) { this.overallScore = s; }
}
