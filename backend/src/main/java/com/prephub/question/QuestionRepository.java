package com.prephub.question;

import org.springframework.data.jpa.repository.*;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {
    Optional<Question> findByFingerprint(String fingerprint);
    List<Question> findByFingerprintIn(java.util.Collection<String> fingerprints);

    @Query("SELECT q.topic.name AS label, COUNT(q) AS total FROM Question q WHERE q.approvalStatus = com.prephub.common.Enums.ApprovalStatus.APPROVED GROUP BY q.topic.name ORDER BY COUNT(q) DESC")
    List<LabelCount> countByTopic();

    @Query("SELECT qc.company.name AS label, COUNT(qc) AS total FROM QuestionCompany qc GROUP BY qc.company.name ORDER BY COUNT(qc) DESC")
    List<LabelCount> countByCompany();

    @Query("SELECT COUNT(q) FROM Question q WHERE q.approvalStatus = com.prephub.common.Enums.ApprovalStatus.APPROVED AND q.answerText IS NOT NULL")
    long countAnswered();

    @Query("SELECT COUNT(q) FROM Question q WHERE q.approvalStatus = com.prephub.common.Enums.ApprovalStatus.APPROVED")
    long countApproved();

    // Company page breakdowns
    @Query("SELECT q.topic.name AS label, COUNT(q) AS total FROM QuestionCompany qc JOIN qc.question q WHERE qc.company.id = :cid GROUP BY q.topic.name ORDER BY COUNT(q) DESC")
    List<LabelCount> companyByTopic(Long cid);
    @Query("SELECT q.difficulty, COUNT(q) FROM QuestionCompany qc JOIN qc.question q WHERE qc.company.id = :cid GROUP BY q.difficulty")
    List<Object[]> companyByDifficultyRaw(Long cid);
    @Query("SELECT COALESCE(qc.interviewRound, 'Unknown') AS label, COUNT(qc) AS total FROM QuestionCompany qc WHERE qc.company.id = :cid GROUP BY qc.interviewRound")
    List<LabelCount> companyByRound(Long cid);
    @Query("SELECT t.name AS label, COUNT(q) AS total FROM QuestionCompany qc JOIN qc.question q JOIN q.tags t WHERE qc.company.id = :cid GROUP BY t.name ORDER BY COUNT(q) DESC")
    List<LabelCount> companyByTag(Long cid);
    @Query("SELECT COUNT(qc) FROM QuestionCompany qc WHERE qc.company.id = :cid AND qc.question.answerText IS NOT NULL")
    long companyAnswered(Long cid);
    @Query("SELECT COUNT(qc) FROM QuestionCompany qc WHERE qc.company.id = :cid")
    long companyTotal(Long cid);

    interface LabelCount { String getLabel(); Long getTotal(); }
}
