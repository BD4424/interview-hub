package com.prephub.ai;

import com.prephub.ai.AiDtos.*;
import com.prephub.question.QuestionDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/questions/{id}")
public class AiController {
    private final AiQuestionService service;
    private final ReviewService review;

    public AiController(AiQuestionService service, ReviewService review) { this.service = service; this.review = review; }

    /** Generates follow-ups (not stored). Synchronous: typically 5–20 s depending on the model. */
    @PostMapping("/follow-ups/generate")
    public FollowUpBatch generate(@PathVariable Long id, @Valid @RequestBody(required = false) GenerateFollowUpsRequest r) {
        return service.generateFollowUps(id, r == null ? new GenerateFollowUpsRequest(null, null, null, null, null, null, null) : r);
    }

    @PostMapping("/follow-ups") @ResponseStatus(HttpStatus.CREATED)
    public SaveFollowUpResult save(@PathVariable Long id, @Valid @RequestBody SaveFollowUpRequest r) { return service.saveForReview(id, r); }

    @GetMapping("/follow-ups")
    public List<LinkedFollowUp> list(@PathVariable Long id) { return service.followUps(id); }

    @PostMapping("/answer/generate")
    public DraftAnswer draft(@PathVariable Long id) { return service.draftAnswer(id); }

    @PostMapping("/approval")
    public ResponseEntity<QuestionDtos.QuestionDto> approval(@PathVariable Long id, @Valid @RequestBody ApprovalRequest r) {
        var dto = review.decide(id, r);
        return dto == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(dto);
    }
}
