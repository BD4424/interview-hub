package com.prephub.study;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/topics/{id}")
public class TopicStudyController {
    private final TopicStudyService service;
    public TopicStudyController(TopicStudyService service) { this.service = service; }

    public record NotesRequest(@Size(max = TopicStudyService.MAX_NOTES) String notes) {}
    public record PathRequest(@Size(max = TopicStudyService.MAX_STEPS) List<Long> tagIds) {}

    @GetMapping("/overview") public TopicStudyService.Overview overview(@PathVariable Long id) { return service.overview(id); }
    @PutMapping("/notes") public TopicStudyService.Overview notes(@PathVariable Long id, @Valid @RequestBody NotesRequest r) { return service.saveNotes(id, r.notes()); }
    @GetMapping("/path") public TopicStudyService.Path path(@PathVariable Long id) { return service.path(id); }
    @PutMapping("/path") public TopicStudyService.Path savePath(@PathVariable Long id, @Valid @RequestBody PathRequest r) { return service.savePath(id, r.tagIds()); }
    /** Back to the automatic path (tags ordered by question count). */
    @DeleteMapping("/path") public TopicStudyService.Path resetPath(@PathVariable Long id) { return service.savePath(id, List.of()); }
}
