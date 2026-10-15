package com.prephub.tag;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {
    private final TagService service;
    public TagController(TagService service) { this.service = service; }

    @GetMapping
    public List<TagService.TagDto> list(@RequestParam(required = false) String q) {
        return q == null || q.isBlank() ? service.listWithUsage() : service.search(q);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public TagService.TagDto create(@Valid @RequestBody TagService.TagRequest r) { return service.create(r); }
    @PutMapping("/{id}") public TagService.TagDto update(@PathVariable Long id, @Valid @RequestBody TagService.TagRequest r) { return service.update(id, r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }
}
