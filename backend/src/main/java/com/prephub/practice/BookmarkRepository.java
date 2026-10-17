package com.prephub.practice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<QuestionBookmark, Long> {
    Optional<QuestionBookmark> findByQuestionId(Long questionId);
    List<QuestionBookmark> findAllByOrderByCreatedAtDesc();
}
