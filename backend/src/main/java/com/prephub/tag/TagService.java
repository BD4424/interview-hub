package com.prephub.tag;

import com.prephub.common.BadRequestException;
import com.prephub.common.NotFoundException;
import com.prephub.common.TextNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TagService {
    private final TagRepository repo;
    private final TagCreator creator;
    public TagService(TagRepository repo, TagCreator creator) { this.repo = repo; this.creator = creator; }

    public record TagDto(Long id, String name, long usage) {}
    public record TagRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description) {}

    @Transactional(readOnly = true)
    public List<TagDto> listWithUsage() {
        return repo.findAllWithUsage().stream().map(u -> new TagDto(u.getId(), u.getName(), u.getUsageCount())).toList();
    }

    @Transactional(readOnly = true)
    public List<TagDto> search(String q) {
        String n = TextNormalizer.normalizeName(q);
        return repo.findTop20ByNormalizedNameContainingOrderByNameAsc(n == null ? "" : n).stream()
                .map(t -> new TagDto(t.getId(), t.getName(), 0)).toList();
    }

    @Transactional
    public TagDto create(TagRequest r) {
        String n = TextNormalizer.normalizeName(r.name());
        if (repo.findByNormalizedName(n).isPresent()) throw new BadRequestException("Tag already exists: " + r.name());
        Tag t = new Tag(); t.setName(TextNormalizer.cleanDisplay(r.name())); t.setNormalizedName(n); t.setDescription(r.description());
        return new TagDto(repo.save(t).getId(), t.getName(), 0);
    }

    @Transactional
    public TagDto update(Long id, TagRequest r) {
        Tag t = repo.findById(id).orElseThrow(() -> new NotFoundException("Tag", id));
        String n = TextNormalizer.normalizeName(r.name());
        repo.findByNormalizedName(n).filter(o -> !o.getId().equals(id)).ifPresent(o -> { throw new BadRequestException("Tag already exists"); });
        t.setName(TextNormalizer.cleanDisplay(r.name())); t.setNormalizedName(n); t.setDescription(r.description());
        return new TagDto(t.getId(), t.getName(), 0);
    }

    @Transactional
    public void delete(Long id) { repo.delete(repo.findById(id).orElseThrow(() -> new NotFoundException("Tag", id))); }

    /**
     * Find-or-create tags by name, normalized and deduplicated. Concurrency-safe: creation happens in a
     * separate transaction and a unique-constraint race falls back to re-reading the winner.
     */
    @Transactional
    public Set<Tag> resolveOrCreate(Collection<String> names) {
        Map<String, String> byNorm = new LinkedHashMap<>();
        for (String raw : names == null ? List.<String>of() : names) {
            String n = TextNormalizer.normalizeName(raw);
            if (n != null) byNorm.putIfAbsent(n, TextNormalizer.cleanDisplay(raw));
        }
        Set<Tag> result = new LinkedHashSet<>();
        for (var e : byNorm.entrySet()) {
            Tag t = repo.findByNormalizedName(e.getKey()).orElseGet(() -> {
                try { return creator.create(e.getValue(), e.getKey()); }
                catch (DataIntegrityViolationException race) { return creator.findFresh(e.getKey()).orElseThrow(() -> race); }
            });
            result.add(t);
        }
        return result;
    }

    /** Returns which of the given names do not exist yet (for import reports). */
    @Transactional(readOnly = true)
    public Set<String> missing(Collection<String> names) {
        Set<String> norm = new LinkedHashSet<>();
        names.forEach(n -> { String x = TextNormalizer.normalizeName(n); if (x != null) norm.add(x); });
        repo.findByNormalizedNameIn(norm).forEach(t -> norm.remove(t.getNormalizedName()));
        return norm;
    }

    @org.springframework.stereotype.Component
    public static class TagCreator {
        private final TagRepository repo;
        public TagCreator(TagRepository repo) { this.repo = repo; }
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public Tag create(String name, String normalized) {
            Tag t = new Tag(); t.setName(name); t.setNormalizedName(normalized);
            return repo.saveAndFlush(t);
        }
        /** New transaction = fresh snapshot, so a row committed by a concurrent winner is visible under REPEATABLE READ. */
        @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
        public java.util.Optional<Tag> findFresh(String normalized) { return repo.findByNormalizedName(normalized); }
    }
}
