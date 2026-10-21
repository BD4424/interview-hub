package com.prephub.practice;

import com.prephub.common.Enums.Difficulty;
import com.prephub.practice.EvaluationValidator.Evaluation;
import com.prephub.practice.PracticeEnums.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PracticeDtos {
    private PracticeDtos() {
    }

    public record StartSessionRequest(@NotNull SessionType type, Long topicId, Long companyId,
                                      @Size(max = 20) List<Long> tagIds,
                                      Difficulty difficulty, @Min(1) @Max(30) Integer questionLimit, Long questionId) {
    }

    public record AnswerRequest(@NotBlank(message = "Write an answer first") @Size(max = 10000) String answer,
                                Confidence confidence, @DecimalMin("0") @DecimalMax("10") Double selfScore) {
    }

    public record SelfScoreRequest(@NotNull @DecimalMin("0") @DecimalMax("10") Double score) {
    }

    public record TurnDto(Long id, int turnNumber, TurnType type, TurnStatus status, Long questionId, String question,
                          String topic, Difficulty difficulty, List<String> tags, List<String> companies,
                          boolean sessionOnly,
                          Long parentTurnId, String userAnswer, Confidence confidence, Double score,
                          Evaluation evaluation,
                          String referenceAnswer, List<String> expectedPoints, boolean followUpAvailable,
                          Instant askedAt, Instant answeredAt) {
    }

    public record SessionDto(Long id, SessionType type, SessionStatus status, String title, String topic,
                             String company,
                             List<String> tags, Difficulty difficulty, int questionLimit, int bankQuestionsAsked,
                             Instant startedAt, Instant completedAt, Double overallScore, boolean aiEnabled,
                             Long currentTurnId, List<TurnDto> turns, String message) {
    }

    public record SessionSummary(Long id, SessionType type, SessionStatus status, String title, int answered,
                                 Double overallScore, Instant startedAt, Instant completedAt) {
    }

    public record QuestionStatus(Long questionId, boolean bookmarked, int attempts, Double lastScore, Double bestScore,
                                 Instant lastAttemptAt, boolean dueForRevision) {
    }

    public record AttemptDto(Long id, Long sessionId, String userAnswer, Double score, Confidence confidence,
                             Evaluation evaluation, Instant attemptedAt) {
    }

    public record BookmarkRequest(@Size(max = 2000) String notes) {
    }

    public record TopicProgress(String topic, int attempts, int questionsAttempted, int totalQuestions,
                                Double averageScore) {
    }

    public record DayProgress(LocalDate date, int attempts, Double averageScore) {
    }

    public record RecentAttempt(Long questionId, String question, String topic, Double score, Instant at) {
    }

    public record ProgressSummary(int totalAttempts, int questionsAttempted, Double averageScore30d, int streakDays,
                                  int todayAttempts, int dueForRevision, int bookmarks, int sessionsCompleted) {
    }

    public record Progress(ProgressSummary summary, List<DayProgress> last30Days, List<TopicProgress> topics,
                           List<TopicProgress> weakTopics, List<SessionSummary> recentSessions,
                           List<RecentAttempt> recentAttempts) {
    }
}
