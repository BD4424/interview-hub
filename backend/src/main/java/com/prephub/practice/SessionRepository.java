package com.prephub.practice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SessionRepository extends JpaRepository<InterviewSession, Long> {
    List<InterviewSession> findTop20ByOrderByStartedAtDesc();
}
