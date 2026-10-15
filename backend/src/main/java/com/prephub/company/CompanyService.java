package com.prephub.company;

import com.prephub.common.BadRequestException;
import com.prephub.common.NotFoundException;
import com.prephub.common.TextNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompanyService {
    private final CompanyRepository repo;
    private final CompanyCreator creator;
    public CompanyService(CompanyRepository repo, CompanyCreator creator) { this.repo = repo; this.creator = creator; }

    public record CompanyDto(Long id, String name, String website, String industry, String notes, long questionCount) {
        static CompanyDto of(Company c, long count) { return new CompanyDto(c.getId(), c.getName(), c.getWebsite(), c.getIndustry(), c.getNotes(), count); }
    }
    public record CompanyRequest(@NotBlank @Size(max = 200) String name, @Size(max = 500) String website,
                                 @Size(max = 200) String industry, @Size(max = 2000) String notes) {}

    @Transactional(readOnly = true)
    public List<CompanyDto> list(String q) {
        if (q != null && !q.isBlank()) {
            String n = TextNormalizer.normalizeName(q);
            return repo.findTop20ByNormalizedNameContainingOrderByNameAsc(n).stream().map(c -> CompanyDto.of(c, 0)).toList();
        }
        return repo.findAllWithCounts().stream().map(c -> new CompanyDto(c.getId(), c.getName(), null, null, null, c.getQuestionCount())).toList();
    }

    @Transactional(readOnly = true)
    public Company get(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Company", id)); }

    @Transactional(readOnly = true)
    public CompanyDto getDto(Long id) { return CompanyDto.of(get(id), 0); }

    @Transactional
    public CompanyDto create(CompanyRequest r) {
        String n = TextNormalizer.normalizeName(r.name());
        repo.findByNormalizedName(n).ifPresent(c -> { throw new BadRequestException("Company already exists: " + c.getName() + " (id " + c.getId() + ")"); });
        Company c = new Company();
        apply(c, r);
        return CompanyDto.of(repo.save(c), 0);
    }

    @Transactional
    public CompanyDto update(Long id, CompanyRequest r) {
        Company c = get(id);
        String n = TextNormalizer.normalizeName(r.name());
        repo.findByNormalizedName(n).filter(o -> !o.getId().equals(id)).ifPresent(o -> { throw new BadRequestException("Another company already has this name"); });
        apply(c, r);
        return CompanyDto.of(c, 0);
    }

    @Transactional
    public void delete(Long id) {
        try { repo.delete(get(id)); repo.flush(); }
        catch (DataIntegrityViolationException e) { throw new BadRequestException("Company has question associations; remove them first"); }
    }

    private void apply(Company c, CompanyRequest r) {
        c.setName(TextNormalizer.cleanDisplay(r.name()));
        c.setNormalizedName(TextNormalizer.normalizeName(r.name()));
        c.setWebsite(TextNormalizer.cleanDisplay(r.website()));
        c.setIndustry(TextNormalizer.cleanDisplay(r.industry()));
        c.setNotes(r.notes());
    }

    public record Resolved(Company company, boolean created) {}

    /** Find existing company by normalized name or create it; race-safe via unique constraint. */
    @Transactional
    public Resolved resolveOrCreate(String rawName) {
        String n = TextNormalizer.normalizeName(rawName);
        if (n == null) throw new BadRequestException("Company name is blank");
        var existing = repo.findByNormalizedName(n);
        if (existing.isPresent()) return new Resolved(existing.get(), false);
        try { return new Resolved(creator.create(TextNormalizer.cleanDisplay(rawName), n), true); }
        catch (DataIntegrityViolationException race) {
            return new Resolved(creator.findFresh(n).orElseThrow(() -> race), false);
        }
    }

    @Transactional(readOnly = true)
    public boolean exists(String rawName) {
        String n = TextNormalizer.normalizeName(rawName);
        return n != null && repo.findByNormalizedName(n).isPresent();
    }

    @Component
    public static class CompanyCreator {
        private final CompanyRepository repo;
        public CompanyCreator(CompanyRepository repo) { this.repo = repo; }
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public Company create(String name, String normalized) {
            Company c = new Company(); c.setName(name); c.setNormalizedName(normalized);
            return repo.saveAndFlush(c);
        }
        @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
        public java.util.Optional<Company> findFresh(String normalized) { return repo.findByNormalizedName(normalized); }
    }
}
