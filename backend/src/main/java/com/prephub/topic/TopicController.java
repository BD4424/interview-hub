package com.prephub.topic;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {
    private final TopicService service;
    public TopicController(TopicService service) { this.service = service; }

    @GetMapping public List<TopicService.TopicDto> list() { return service.list(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public TopicService.TopicDto create(@Valid @RequestBody TopicService.TopicRequest r) { return service.create(r); }
    @PutMapping("/{id}") public TopicService.TopicDto update(@PathVariable Long id, @Valid @RequestBody TopicService.TopicRequest r) { return service.update(id, r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }
}
