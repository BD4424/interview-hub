package com.prephub.mocktest;

import com.prephub.common.Enums.Difficulty;
import com.prephub.mocktest.MockTestService.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mock-tests")
public class MockTestController {
    private final MockTestService service;
    public MockTestController(MockTestService service) { this.service = service; }

    @GetMapping public List<TestSummary> list() { return service.list(); }

    @GetMapping("/available")
    public Available available(@RequestParam Scope scope, @RequestParam(required = false) List<Long> topicIds,
                               @RequestParam(required = false) Difficulty difficulty) {
        return service.available(scope, topicIds, difficulty);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public TestDetail create(@Valid @RequestBody CreateRequest r) { return service.create(r); }

    @GetMapping("/{id}") public TestDetail get(@PathVariable Long id) { return service.get(id); }

    /** Autosave: send answer and/or flagged; a null field is left unchanged, an empty answer clears it. */
    @PutMapping("/{id}/items/{itemId}")
    public SaveResult save(@PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody AnswerRequest r) { return service.save(id, itemId, r); }

    @PostMapping("/{id}/submit") public TestDetail submit(@PathVariable Long id) { return service.submit(id); }
    @PostMapping("/{id}/self-score") public TestDetail selfScore(@PathVariable Long id, @Valid @RequestBody SelfScoreRequest r) { return service.selfScore(id, r); }
    @PostMapping("/{id}/grade-with-ai") public TestDetail gradeWithAi(@PathVariable Long id) { return service.gradeWithAi(id); }
    @PostMapping("/{id}/retry-failed") public TestDetail retryFailed(@PathVariable Long id) { return service.retryFailed(id); }

    @PostMapping("/{id}/retake") @ResponseStatus(HttpStatus.CREATED)
    public TestDetail retake(@PathVariable Long id, @Valid @RequestBody(required = false) RetakeRequest r) { return service.retake(id, r); }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }
}
