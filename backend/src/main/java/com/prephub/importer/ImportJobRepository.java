package com.prephub.importer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
    List<ImportJob> findTop5ByOrderByCreatedAtDesc();
    List<ImportJob> findAllByOrderByCreatedAtDesc();
    List<ImportJob> findByFileHashAndIdNot(String fileHash, Long id);
}
