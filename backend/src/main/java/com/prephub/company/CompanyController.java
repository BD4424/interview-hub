package com.prephub.company;

import com.prephub.practice.PracticeStats;
import com.prephub.question.QuestionRepository;
import com.prephub.question.QuestionRepository.LabelCount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {
    private final CompanyService service;
    private final QuestionRepository questions;
    private final PracticeStats practice;

    public CompanyController(CompanyService service, QuestionRepository questions, PracticeStats practice) {
        this.service = service; this.questions = questions; this.practice = practice;
    }

    public record Bucket(String label, long count) {}
    public record CompanySummary(CompanyService.CompanyDto company, long totalQuestions, long answered, long unanswered,
                                 long bookmarked, long practiced,
                                 List<Bucket> byTopic, List<Bucket> byDifficulty, List<Bucket> byRound, List<Bucket> byTag) {}

    @GetMapping public List<CompanyService.CompanyDto> list(@RequestParam(required = false) String q) { return service.list(q); }
    @GetMapping("/{id}") public CompanyService.CompanyDto get(@PathVariable Long id) { return service.getDto(id); }

    @GetMapping("/{id}/summary") @Transactional(readOnly = true)
    public CompanySummary summary(@PathVariable Long id) {
        var c = service.getDto(id);
        long total = questions.companyTotal(id), answered = questions.companyAnswered(id);
        return new CompanySummary(c, total, answered, total - answered, practice.companyBookmarked(id), practice.companyPracticed(id),
                map(questions.companyByTopic(id)),
                questions.companyByDifficultyRaw(id).stream().map(r -> new Bucket(r[0] == null ? "UNSPECIFIED" : r[0].toString(), ((Number) r[1]).longValue())).toList(),
                map(questions.companyByRound(id)), map(questions.companyByTag(id)));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CompanyService.CompanyDto create(@Valid @RequestBody CompanyService.CompanyRequest r) { return service.create(r); }
    @PutMapping("/{id}") public CompanyService.CompanyDto update(@PathVariable Long id, @Valid @RequestBody CompanyService.CompanyRequest r) { return service.update(id, r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }

    private static List<Bucket> map(List<LabelCount> l) { return l.stream().map(x -> new Bucket(x.getLabel(), x.getTotal())).toList(); }
}
