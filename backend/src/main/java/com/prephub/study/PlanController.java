package com.prephub.study;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {
    private final PlanService service;
    public PlanController(PlanService service) { this.service = service; }

    @GetMapping public List<PlanService.PlanSummary> list() { return service.list(); }

    @GetMapping("/upcoming")
    public ResponseEntity<PlanService.PlanSummary> upcoming() {
        return service.upcoming().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}") public PlanService.PlanDetail get(@PathVariable Long id) { return service.detail(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public PlanService.PlanDetail create(@Valid @RequestBody PlanService.PlanRequest r) { return service.create(r); }
    @PutMapping("/{id}") public PlanService.PlanDetail update(@PathVariable Long id, @Valid @RequestBody PlanService.PlanRequest r) { return service.update(id, r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }
}
