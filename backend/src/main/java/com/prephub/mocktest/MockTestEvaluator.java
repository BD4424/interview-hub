package com.prephub.mocktest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prephub.practice.AnswerEvaluator;
import com.prephub.practice.EvaluationValidator.Evaluation;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Grades submitted answers in the background with the same AI evaluator as practice sessions. Answers are graded in
 * parallel (prephub.mock-test.parallelism, default 4); each result is stored as soon as it arrives, so a restart only
 * re-grades what is still pending (see {@link MockTestSweeper}). A failed answer is marked FAILED and can be retried.
 */
@Component
public class MockTestEvaluator {
    private static final Logger log = LoggerFactory.getLogger(MockTestEvaluator.class);

    private final JdbcTemplate jdbc;
    private final AnswerEvaluator evaluator;
    private final ObjectMapper json;
    private final MockTestCompleter completer;
    private final boolean async;
    private final ExecutorService coordinator;
    private final ExecutorService workers;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public MockTestEvaluator(JdbcTemplate jdbc, AnswerEvaluator evaluator, ObjectMapper json, MockTestCompleter completer,
                             @Value("${prephub.mock-test.async:true}") boolean async,
                             @Value("${prephub.mock-test.parallelism:4}") int parallelism) {
        this.jdbc = jdbc; this.evaluator = evaluator; this.json = json; this.completer = completer; this.async = async;
        this.coordinator = Executors.newFixedThreadPool(2, named("mock-test-coordinator"));
        this.workers = Executors.newFixedThreadPool(Math.max(1, Math.min(parallelism, 16)), named("mock-test-grader"));
    }

    private static ThreadFactory named(String prefix) {
        return r -> { Thread t = new Thread(r, prefix); t.setDaemon(true); return t; };
    }

    public boolean isRunning(Long testId) { return running.contains(testId); }

    /** Starts grading (no-op if this test is already being graded). Synchronous when prephub.mock-test.async=false. */
    public void start(Long testId) {
        if (!running.add(testId)) return;
        Runnable job = () -> {
            try { evaluateNow(testId); }
            catch (RuntimeException e) { log.warn("Mock test {} grading failed: {}", testId, e.getClass().getSimpleName()); }
            finally { running.remove(testId); }
        };
        if (async) coordinator.submit(job); else job.run();
    }

    private record Pending(Long id, Long questionId, String question, String topic, String difficulty, String answer) {}

    void evaluateNow(Long testId) {
        List<Pending> items = jdbc.query("SELECT id, question_id, question_text, topic_name, difficulty, user_answer FROM mock_test_items " +
                        "WHERE test_id = ? AND status = 'ANSWERED' ORDER BY item_no",
                (rs, i) -> new Pending(rs.getLong(1), rs.getObject(2, Long.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)), testId);
        if (async) {
            List<Future<?>> futures = new ArrayList<>();
            for (Pending p : items) futures.add(workers.submit(() -> grade(p)));
            for (Future<?> f : futures) {
                try { f.get(10, TimeUnit.MINUTES); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                catch (ExecutionException | TimeoutException e) { /* the item records its own failure */ }
            }
        } else {
            items.forEach(this::grade);
        }
        completer.complete(testId);
    }

    private void grade(Pending p) {
        Timestamp now = Timestamp.from(Instant.now());
        try {
            String reference = p.questionId() == null ? null : jdbc.queryForList("SELECT answer_text FROM questions WHERE id = ?", String.class, p.questionId())
                    .stream().findFirst().orElse(null);
            Evaluation e = evaluator.evaluate(p.question(), p.topic(), p.difficulty(), reference, List.of(), p.answer());
            jdbc.update("UPDATE mock_test_items SET status = 'EVALUATED', score = ?, feedback = ?, error_message = NULL, evaluated_at = ? " +
                    "WHERE id = ? AND status = 'ANSWERED'", e.score(), json.writeValueAsString(e), now, p.id());
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (msg.length() > 480) msg = msg.substring(0, 480);
            jdbc.update("UPDATE mock_test_items SET status = 'FAILED', error_message = ?, evaluated_at = ? WHERE id = ? AND status = 'ANSWERED'", msg, now, p.id());
        }
    }

    @PreDestroy
    void shutdown() { coordinator.shutdownNow(); workers.shutdownNow(); }
}
