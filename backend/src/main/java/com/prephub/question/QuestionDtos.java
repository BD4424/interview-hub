package com.prephub.question;

import com.prephub.common.Enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class QuestionDtos {
    private QuestionDtos() {}

    public record CompanyAttributionRequest(
            Long companyId,                        // existing company, or
            @Size(max = 200) String companyName,   // create-or-match by normalized name
            @Size(max = 200) String role, @Size(max = 100) String interviewRound, @Size(max = 100) String jobLevel,
            LocalDate dateAsked, @Size(max = 500) String sourceReference, AttributionStatus attributionStatus,
            @Size(max = 2000) String notes) {}

    public record QuestionRequest(
            @NotBlank(message = "Question text is required") @Size(max = 10000) String questionText,
            @Size(max = 50000) String answerText,
            @NotNull(message = "Primary topic is required") Long topicId,
            List<@NotBlank @Size(max = 120) String> tags,
            Difficulty difficulty, QuestionType questionType, AnswerStatus answerStatus,
            @Size(max = 500) String sourceReference, @Size(max = 5000) String notes,
            List<@Valid CompanyAttributionRequest> companies) {}

    public record CompanyAttributionDto(Long associationId, Long companyId, String companyName, String role, String interviewRound,
                                        String jobLevel, LocalDate dateAsked, String sourceReference,
                                        AttributionStatus attributionStatus, String notes) {}

    public record TagRef(Long id, String name) {}

    public record QuestionDto(Long id, String questionText, String answerText, Long topicId, String topicName,
                              List<TagRef> tags, Difficulty difficulty, QuestionType questionType,
                              AnswerStatus answerStatus, SourceType sourceType, String sourceReference, String notes,
                              ApprovalStatus approvalStatus, List<CompanyAttributionDto> companies,
                              Instant createdAt, Instant updatedAt) {}

    public static QuestionDto toDto(Question q) {
        return new QuestionDto(q.getId(), q.getQuestionText(), q.getAnswerText(), q.getTopic().getId(), q.getTopic().getName(),
                q.getTags().stream().map(t -> new TagRef(t.getId(), t.getName())).toList(),
                q.getDifficulty(), q.getQuestionType(), q.getAnswerStatus(), q.getSourceType(), q.getSourceReference(), q.getNotes(),
                q.getApprovalStatus(),
                q.getCompanies().stream().map(c -> new CompanyAttributionDto(c.getId(), c.getCompany().getId(), c.getCompany().getName(),
                        c.getRole(), c.getInterviewRound(), c.getJobLevel(), c.getDateAsked(), c.getSourceReference(),
                        c.getAttributionStatus(), c.getNotes())).toList(),
                q.getCreatedAt(), q.getUpdatedAt());
    }
}
