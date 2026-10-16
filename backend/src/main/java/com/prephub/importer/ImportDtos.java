package com.prephub.importer;

import com.prephub.common.Enums.*;
import com.prephub.question.QuestionDtos.QuestionDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ImportDtos {
    private ImportDtos() {}

    public record ImportReport(int totalExtracted, int imported, int merged, int exactDuplicatesSkipped, int duplicatesInFile,
                               int needsReview, int pendingApproval, int invalid, int rejected,
                               int withoutAnswer, int withoutCompany,
                               List<String> companiesNotInDirectory, List<String> tagsNotInDirectory) {}

    public record ImportJobDto(Long id, String filename, String format, ImportStatus status, AttributionMode attributionMode,
                               Long defaultCompanyId, String defaultCompanyName, Long defaultTopicId,
                               String attributionPolicy, Instant createdAt, Instant completedAt, String errorMessage,
                               List<String> parseNotes, List<Long> previousImportsOfSameFile, ImportReport report) {}

    public record CandidateDto(Long id, String sourceReference, String question, String answer, String topic,
                               List<String> tags, List<String> companies, String difficulty, String questionType,
                               String interviewRound, String role, String jobLevel,
                               MatchType matchType, ReviewStatus reviewStatus, String reason,
                               Long matchedQuestionId, Double similarityScore, QuestionDto matchedQuestion,
                               Long createdQuestionId, List<String> warnings) {}

    public record ApproveRequest(List<Long> candidateIds) {}

    public record ResolveRequest(@NotNull ReviewAction action, @Size(max = 1000) String reason) {}

    public record EditCandidateRequest(@Size(max = 10000) String question, @Size(max = 50000) String answer,
                                       @Size(max = 120) String topic, List<String> tags, List<String> companies,
                                       String difficulty, String questionType, String interviewRound, String role, String jobLevel) {}

    /** Preview returned before the import job is created (wizard steps 2–5). */
    public record CandidatePreview(String reference, String question, boolean hasAnswer, String topic,
                                   List<String> tags, List<String> companies, String difficulty) {}

    public record InspectResult(String filename, String format, boolean tabular, List<String> headers,
                                Map<String, String> mapping, List<String> fields, int candidateCount,
                                List<CandidatePreview> sample, List<String> notes, String error) {}
}
