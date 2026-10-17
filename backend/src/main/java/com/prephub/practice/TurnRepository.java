package com.prephub.practice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TurnRepository extends JpaRepository<InterviewTurn, Long> {
    List<InterviewTurn> findBySessionIdOrderByTurnNumberAsc(Long sessionId);
    List<InterviewTurn> findByParentTurnId(Long parentTurnId);
}
