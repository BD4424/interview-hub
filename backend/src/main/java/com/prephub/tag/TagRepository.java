package com.prephub.tag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByNormalizedName(String normalizedName);
    List<Tag> findByNormalizedNameIn(Collection<String> names);
    List<Tag> findTop20ByNormalizedNameContainingOrderByNameAsc(String fragment);

    interface TagUsage { Long getId(); String getName(); Long getUsageCount(); }

    @Query("SELECT t.id AS id, t.name AS name, " +
            "(SELECT COUNT(q) FROM Question q JOIN q.tags qt WHERE qt = t) AS usageCount " +
            "FROM Tag t ORDER BY usageCount DESC, t.name ASC")
    List<TagUsage> findAllWithUsage();
}
