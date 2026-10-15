package com.prephub.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByNormalizedName(String normalizedName);
    List<Company> findTop20ByNormalizedNameContainingOrderByNameAsc(String fragment);

    interface CompanyCount { Long getId(); String getName(); Long getQuestionCount(); }

    @Query("SELECT c.id AS id, c.name AS name, " +
            "(SELECT COUNT(qc) FROM QuestionCompany qc WHERE qc.company = c) AS questionCount " +
            "FROM Company c ORDER BY c.name ASC")
    List<CompanyCount> findAllWithCounts();
}
