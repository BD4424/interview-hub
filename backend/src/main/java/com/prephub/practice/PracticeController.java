package com.prephub.practice;

import com.prephub.practice.PracticeDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/interviews")
public class PracticeController {
    private final PracticeService service;

    public PracticeController(PracticeService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionDto start(@Valid @RequestBody StartSessionRequest r) {
        return service.start(r);
    }

    @GetMapping
    public List<SessionSummary> recent() {
        return service.recent();
    }

    @GetMapping("/{id}")
    public SessionDto get(@PathVariable Long id) {
        return service.get(id, null);
    }

    /**
     * Submit an answer. AI evaluation is synchronous (typically 5-20 s). With selfScore the AI is not called.
     */
    @PostMapping("/{id}/answers")
    public SessionDto answer(@PathVariable Long id, @RequestParam Long turnId, @Valid @RequestBody AnswerRequest r) {
        return service.answer(id, turnId, r);
    }

    @PostMapping("/{id}/turns/{turnId}/self-score")
    public SessionDto selfScore(@PathVariable Long id, @PathVariable Long turnId, @Valid @RequestBody SelfScoreRequest r) {
        return service.selfScore(id, turnId, r);
    }

    @PostMapping("/{id}/turns/{turnId}/skip")
    public SessionDto skip(@PathVariable Long id, @PathVariable Long turnId) {
        return service.skip(id, turnId);
    }

    @PostMapping("/{id}/turns/{turnId}/follow-up")
    public SessionDto followUp(@PathVariable Long id, @PathVariable Long turnId) {
        return service.followUp(id, turnId);
    }

    @PostMapping("/{id}/next")
    public SessionDto next(@PathVariable Long id) {
        return service.next(id);
    }

    @PostMapping("/{id}/complete")
    public SessionDto complete(@PathVariable Long id) {
        return service.complete(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
