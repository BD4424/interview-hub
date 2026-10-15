package com.prephub.topic;

import com.prephub.common.BadRequestException;
import com.prephub.common.NotFoundException;
import com.prephub.common.TextNormalizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TopicService {
    public static final String FALLBACK_SLUG = "general";
    private final TopicRepository repo;
    public TopicService(TopicRepository repo) { this.repo = repo; }

    public record TopicDto(Long id, String name, String slug, String description) {
        static TopicDto of(Topic t) { return new TopicDto(t.getId(), t.getName(), t.getSlug(), t.getDescription()); }
    }
    public record TopicRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 120) String name,
                               @jakarta.validation.constraints.Size(max = 1000) String description) {}

    @Transactional(readOnly = true)
    public List<TopicDto> list() { return repo.findAll(org.springframework.data.domain.Sort.by("name")).stream().map(TopicDto::of).toList(); }

    @Transactional(readOnly = true)
    public Topic get(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Topic", id)); }

    @Transactional
    public TopicDto create(TopicRequest req) {
        String slug = TextNormalizer.slugify(req.name());
        if (slug == null) throw new BadRequestException("Topic name is required");
        if (repo.findBySlug(slug).isPresent()) throw new BadRequestException("Topic already exists: " + req.name());
        Topic t = new Topic();
        t.setName(TextNormalizer.cleanDisplay(req.name())); t.setSlug(slug); t.setDescription(req.description());
        return TopicDto.of(repo.save(t));
    }

    @Transactional
    public TopicDto update(Long id, TopicRequest req) {
        Topic t = get(id);
        String slug = TextNormalizer.slugify(req.name());
        repo.findBySlug(slug).filter(o -> !o.getId().equals(id)).ifPresent(o -> { throw new BadRequestException("Topic already exists"); });
        t.setName(TextNormalizer.cleanDisplay(req.name())); t.setSlug(slug); t.setDescription(req.description());
        return TopicDto.of(t);
    }

    @Transactional
    public void delete(Long id) {
        try { repo.delete(get(id)); repo.flush(); }
        catch (DataIntegrityViolationException e) { throw new BadRequestException("Topic is used by questions; reassign them first"); }
    }

    /** Resolve topic by name (import); unknown or blank -> fallback "General". Never creates topics silently. */
    @Transactional(readOnly = true)
    public Topic resolveByNameOrFallback(String name, Long defaultTopicId) {
        if (name != null) {
            String slug = TextNormalizer.slugify(name);
            if (slug != null) { var t = repo.findBySlug(slug); if (t.isPresent()) return t.get(); }
        }
        if (defaultTopicId != null) return get(defaultTopicId);
        return repo.findBySlug(FALLBACK_SLUG).orElseThrow(() -> new IllegalStateException("Fallback topic missing"));
    }
}
