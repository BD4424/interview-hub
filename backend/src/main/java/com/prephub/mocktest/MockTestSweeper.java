package com.prephub.mocktest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/** Submits timed-out tests (tab closed when time ran out) and resumes grading interrupted by a restart. */
@Component
public class MockTestSweeper {
    private static final Logger log = LoggerFactory.getLogger(MockTestSweeper.class);
    private final JdbcTemplate jdbc;
    private final MockTestService service;
    private final MockTestEvaluator evaluator;

    public MockTestSweeper(JdbcTemplate jdbc, MockTestService service, MockTestEvaluator evaluator) {
        this.jdbc = jdbc; this.service = service; this.evaluator = evaluator;
    }

    @Scheduled(initialDelayString = "${prephub.mock-test.sweep-initial-delay-ms:30000}", fixedDelayString = "${prephub.mock-test.sweep-interval-ms:60000}")
    public void sweep() {
        try {
            Timestamp cutoff = Timestamp.from(Instant.now().minusSeconds(MockTestService.GRACE_SECONDS));
            for (Long id : jdbc.queryForList("SELECT id FROM mock_tests WHERE status = 'IN_PROGRESS' AND deadline_at IS NOT NULL AND deadline_at < ?", Long.class, cutoff))
                service.submit(id);
            for (Long id : jdbc.queryForList("SELECT id FROM mock_tests WHERE status = 'EVALUATING'", Long.class))
                if (!evaluator.isRunning(id)) evaluator.start(id);
        } catch (RuntimeException e) {
            log.warn("Mock test sweep failed: {}", e.getClass().getSimpleName());
        }
    }
}
