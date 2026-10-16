package com.prephub.importer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prephub.common.BadRequestException;
import com.prephub.common.Enums.AttributionMode;
import com.prephub.common.Enums.ReviewStatus;
import com.prephub.importer.ImportDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {
    private final ImportService service;
    private final ObjectMapper json;
    public ImportController(ImportService service, ObjectMapper json) { this.service = service; this.json = json; }

    /** Preview without storing anything. {@code columnMapping} is a JSON object: {"Header": "question", ...}. */
    @PostMapping(path = "/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InspectResult inspect(@RequestPart("file") MultipartFile file, @RequestParam(required = false) String columnMapping) {
        return service.inspect(ImportService.Upload.of(file), mapping(columnMapping));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportJobDto upload(@RequestPart("file") MultipartFile file,
                               @RequestParam(defaultValue = "NONE") AttributionMode attributionMode,
                               @RequestParam(required = false) Long defaultCompanyId,
                               @RequestParam(required = false) Long defaultTopicId,
                               @RequestParam(required = false) String tags,
                               @RequestParam(required = false) String columnMapping) {
        List<String> extra = tags == null ? List.of() : Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return service.start(ImportService.Upload.of(file), attributionMode, defaultCompanyId, defaultTopicId, extra, mapping(columnMapping), true);
    }

    private Map<String, String> mapping(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try { return json.readValue(raw, new TypeReference<Map<String, String>>() {}); }
        catch (Exception e) { throw new BadRequestException("columnMapping must be a JSON object of column -> field"); }
    }

    @GetMapping public List<ImportJobDto> list() { return service.list(); }
    @GetMapping("/{id}") public ImportJobDto get(@PathVariable Long id) { return service.get(id); }

    @GetMapping("/{id}/candidates")
    public List<CandidateDto> candidates(@PathVariable Long id, @RequestParam(required = false) ReviewStatus status) {
        return service.candidates(id, status);
    }

    @PostMapping("/{id}/approve")
    public ImportJobDto approve(@PathVariable Long id, @RequestBody(required = false) ApproveRequest r) {
        return service.approve(id, r == null ? null : r.candidateIds());
    }

    @PostMapping("/{id}/candidates/{cid}/resolve")
    public CandidateDto resolve(@PathVariable Long id, @PathVariable Long cid, @Valid @RequestBody ResolveRequest r) {
        return service.resolve(id, cid, r);
    }

    @PutMapping("/{id}/candidates/{cid}")
    public CandidateDto edit(@PathVariable Long id, @PathVariable Long cid, @Valid @RequestBody EditCandidateRequest r) {
        return service.edit(id, cid, r);
    }

    @GetMapping("/{id}/failed-rows.csv")
    public ResponseEntity<byte[]> failed(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"import-" + id + "-failed-rows.csv\"")
                .contentType(new MediaType("text", "csv")).body(service.failedRowsCsv(id));
    }
}
