package com.prephub.mocktest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Finishes a test once no answer is waiting for grading: stores the overall score and records one practice attempt
 * per graded bank question (mode MOCK_TEST, timestamped at submission) so mock tests count towards progress,
 * revision and streaks. Idempotent and safe under concurrent calls (row lock + attempt_recorded flag).
 */
@Component
public class MockTestCompleter {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public MockTestCompleter(JdbcTemplate jdbc, TransactionTemplate tx) { this.jdbc = jdbc; this.tx = tx; }

    private record TestState(String status, Timestamp submittedAt) {}
    private record Graded(Long itemId, Long questionId, String answer, Double score, String feedback) {}

    public boolean complete(Long testId) {
        return Boolean.TRUE.equals(tx.execute(s -> {
            List<TestState> rows = jdbc.query("SELECT status, submitted_at FROM mock_tests WHERE id = ? FOR UPDATE",
                    (rs, i) -> new TestState(rs.getString(1), rs.getTimestamp(2)), testId);
            if (rows.isEmpty() || !"EVALUATING".equals(rows.get(0).status())) return false;
            Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM mock_test_items WHERE test_id = ? AND status = 'ANSWERED'", Integer.class, testId);
            if (pending != null && pending > 0) return false;

            List<MockTestMath.ItemScore> scores = jdbc.query(
                    "SELECT topic_name, difficulty, score, user_answer, status FROM mock_test_items WHERE test_id = ?",
                    (rs, i) -> new MockTestMath.ItemScore(rs.getString(1), rs.getString(2), (Double) rs.getObject(3, Double.class),
                            rs.getString(4) != null, "FAILED".equals(rs.getString(5))), testId);
            MockTestMath.Totals totals = MockTestMath.totals(scores);
            Timestamp now = Timestamp.from(Instant.now());
            jdbc.update("UPDATE mock_tests SET status = 'COMPLETED', completed_at = ?, overall_score = ? WHERE id = ?", now, totals.overall(), testId);

            Timestamp at = rows.get(0).submittedAt() != null ? rows.get(0).submittedAt() : now;
            List<Graded> graded = jdbc.query("SELECT id, question_id, user_answer, score, feedback FROM mock_test_items " +
                            "WHERE test_id = ? AND status = 'EVALUATED' AND question_id IS NOT NULL AND attempt_recorded = FALSE",
                    (rs, i) -> new Graded(rs.getLong(1), rs.getLong(2), rs.getString(3), (Double) rs.getObject(4, Double.class), rs.getString(5)), testId);
            for (Graded g : graded) {
                if (jdbc.update("UPDATE mock_test_items SET attempt_recorded = TRUE WHERE id = ? AND attempt_recorded = FALSE", g.itemId()) != 1) continue;
                jdbc.update("INSERT INTO question_attempts (question_id, session_id, user_answer, score, confidence, feedback, attempted_at, mode) " +
                        "VALUES (?, NULL, ?, ?, NULL, ?, ?, 'MOCK_TEST')", g.questionId(), g.answer(), g.score(), g.feedback(), at);
            }
            return true;
        }));
    }
}
