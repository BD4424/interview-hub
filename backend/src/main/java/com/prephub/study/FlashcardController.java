package com.prephub.study;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flashcards")
public class FlashcardController {
    private final FlashcardService service;
    public FlashcardController(FlashcardService service) { this.service = service; }

    public record GradeRequest(@NotNull FlashcardService.Grade grade) {}

    @GetMapping
    public FlashcardService.Deck deck(@RequestParam(required = false) Long topicId,
                                      @RequestParam(required = false) Long companyId,
                                      @RequestParam(required = false) List<Long> tagIds,
                                      @RequestParam(defaultValue = "DUE") FlashcardService.Scope scope,
                                      @RequestParam(defaultValue = "20") int limit,
                                      @RequestParam(defaultValue = "false") boolean answeredOnly) {
        return service.deck(topicId, companyId, tagIds, scope, limit, answeredOnly);
    }

    @PostMapping("/{questionId}/grade")
    public FlashcardService.GradeResult grade(@PathVariable Long questionId, @Valid @RequestBody GradeRequest r) {
        return service.grade(questionId, r.grade());
    }
}
