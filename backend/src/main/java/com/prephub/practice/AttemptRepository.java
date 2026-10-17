package com.prephub.practice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AttemptRepository extends JpaRepository<QuestionAttempt, Long> {
    List<QuestionAttempt> findByQuestionIdOrderByAttemptedAtDesc(Long questionId);
}
