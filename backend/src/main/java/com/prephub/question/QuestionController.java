package com.prephub.question;

import com.prephub.common.Enums.*;
import com.prephub.common.PageResponse;
import com.prephub.question.QuestionDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class QuestionController {
    private final QuestionService service;

    public QuestionController(QuestionService service) {
        this.service = service;
    }

    @GetMapping("/questions")
    public PageResponse<QuestionDto> search(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) Long topicId,
                                            @RequestParam(required = false) Long companyId,
                                            @RequestParam(required = false) List<Long> tagIds,
                                            @RequestParam(defaultValue = "false") boolean matchAllTags,
                                            @RequestParam(required = false) Difficulty difficulty,
                                            @RequestParam(required = false) QuestionType type,
                                            @RequestParam(required = false) Boolean hasAnswer,
                                            @RequestParam(required = false) ApprovalStatus approval,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(defaultValue = "newest") String sort) {
        return service.search(new QuestionSpecifications.Filter(q, topicId, companyId, tagIds, matchAllTags, difficulty, type, hasAnswer, approval), page, size, sort);
    }

    @GetMapping("/companies/{companyId}/questions")
    public PageResponse<QuestionDto> byCompany(@PathVariable Long companyId,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) Long topicId,
                                               @RequestParam(required = false) List<Long> tagIds,
                                               @RequestParam(required = false) Difficulty difficulty,
                                               @RequestParam(required = false) Boolean hasAnswer,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.search(new QuestionSpecifications.Filter(q, topicId, companyId, tagIds, false, difficulty, null, hasAnswer, null), page, size, "newest");
    }

    @GetMapping("/questions/{id}")
    public QuestionDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionDto create(@Valid @RequestBody QuestionRequest r) {
        return service.create(r);
    }

    @PutMapping("/questions/{id}")
    public QuestionDto update(@PathVariable Long id, @Valid @RequestBody QuestionRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/questions/{id}/companies")
    public QuestionDto addCompany(@PathVariable Long id, @Valid @RequestBody CompanyAttributionRequest r) {
        return service.addCompany(id, r);
    }

    @DeleteMapping("/questions/{id}/companies/{companyId}")
    public QuestionDto removeCompany(@PathVariable Long id, @PathVariable Long companyId) {
        return service.removeCompany(id, companyId);
    }
}
