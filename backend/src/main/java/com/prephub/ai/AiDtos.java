package com.prephub.ai;

import com.prephub.common.Enums.ApprovalStatus;
import com.prephub.common.Enums.Difficulty;
import jakarta.validation.constraints.*;

import java.util.List;

public final class AiDtos {
    private AiDtos() {}

    public enum FollowUpMode { SESSION_ONLY, REVIEW }
    /** SESSION_ONLY: lives only in the current practice; REVIEW_ELIGIBLE: may be saved for review; LINK_EXISTING: already in the bank. */
    public enum Persistence { SESSION_ONLY, REVIEW_ELIGIBLE, LINK_EXISTING }

    public record GenerateFollowUpsRequest(
            @Min(1) @Max(5) Integer count,
            FollowUpMode mode,
            Long companyId,                       // optional style context chosen by the user
            @Size(max = 200) String role,
            @Size(max = 100) String interviewRound,
            @Size(max = 10000) String previousAnswer,
            @Size(max = 30) List<@Size(max = 2000) String> sessionQuestions) {}

    public record DuplicateInfo(String matchType, Long matchingQuestionId, String matchingText, ApprovalStatus matchingApproval, Double similarity) {}

    public record FollowUpItem(Long parentQuestionId, String question, List<String> expectedAnswerPoints, Difficulty difficulty,
                               List<String> existingTags, List<String> suggestedNewTags, String reason,
                               Persistence persistence, DuplicateInfo duplicate) {}

    public record FollowUpBatch(Long parentQuestionId, String model, String companyContext, String disclaimer,
                                List<FollowUpItem> items, List<String> rejected) {}

    public record SaveFollowUpRequest(
            @NotBlank @Size(max = 1000) String question,
            @NotEmpty @Size(max = 10) List<@Size(max = 500) String> expectedAnswerPoints,
            String difficulty,
            @Size(max = 8) List<@Size(max = 60) String> tags,
            @NotBlank @Size(max = 500) String reason) {}

    public enum SaveStatus { CREATED_PENDING_REVIEW, LINKED_EXISTING }
    public record SaveFollowUpResult(SaveStatus status, Long questionId, ApprovalStatus approvalStatus, String message) {}

    public record LinkedFollowUp(Long questionId, String question, ApprovalStatus approvalStatus, String difficulty, Integer sequence) {}

    public record DraftAnswer(Long questionId, String model, String draft, String note) {}

    public record ApprovalRequest(@NotNull Decision decision, List<@Size(max = 120) String> applyTags) {}
    public enum Decision { APPROVE, REJECT }
}
