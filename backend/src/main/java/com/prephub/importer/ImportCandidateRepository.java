package com.prephub.importer;

import com.prephub.common.Enums.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ImportCandidateRepository extends JpaRepository<ImportCandidate, Long> {
    List<ImportCandidate> findByJobIdOrderByIdAsc(Long jobId);
    long countByReviewStatus(ReviewStatus status);
}
