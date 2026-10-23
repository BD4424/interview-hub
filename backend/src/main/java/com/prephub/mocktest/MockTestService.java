package com.prephub.mocktest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prephub.ai.AiExceptions;
import com.prephub.common.BadRequestException;
import com.prephub.common.Enums.Difficulty;
import com.prephub.common.NotFoundException;
import com.prephub.practice.AnswerEvaluator;
import com.prephub.practice.EvaluationValidator;
import com.prephub.practice.EvaluationValidator.Evaluation;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Full-length mock tests. Flow: create → answer (autosaved, nothing is graded or revealed) → submit → background
 * grading → one overall score at the end with per-question feedback.
 * Statuses: IN_PROGRESS → EVALUATING → COMPLETED, or IN_PROGRESS → AWAITING_SELF_SCORE → COMPLETED when AI is off.
 */
@Service
public class MockTestService {
    public static final int MAX_QUESTIONS = 60, DEFAULT_QUESTIONS = 20, RECENT_DAYS = 3;
    public static final long GRACE_SECONDS = 120;

    public enum Scope { FULL, TOPICS, CUSTOM }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MockTestEvaluator evaluator;
    private final MockTestCompleter completer;
    private final AnswerEvaluator ai;
    private final ObjectMapper json;

    public MockTestService(JdbcTemplate jdbc, TransactionTemplate tx, MockTestEvaluator evaluator, MockTestCompleter completer,
                           AnswerEvaluator ai, ObjectMapper json) {
        this.jdbc = jdbc; this.tx = tx; this.evaluator = evaluator; this.completer = completer; this.ai = ai; this.json = json;
    }

    // ------------------------------------------------------------------ DTOs

    public record CreateRequest(@NotNull Scope scope, @Size(max = 30) List<Long> topicIds, Difficulty difficulty,
                                @Min(1) @Max(MAX_QUESTIONS) Integer questionCount, @Min(1) @Max(300) Integer timeLimitMinutes,
                                @Size(max = MAX_QUESTIONS) List<Long> questionIds, @Size(max = 200) String title) {}
    public record AnswerRequest(@Size(max = 20000) String answer, Boolean flagged) {}
    public record SaveResult(Long itemId, String status, boolean flagged, Instant savedAt) {}
    public record SelfScoreRequest(@NotNull Map<Long, Double> scores) {}
    public record RetakeRequest(Boolean onlyWeak, @Min(1) @Max(300) Integer timeLimitMinutes) {}
    public record Available(int available) {}

    public record ItemDto(Long id, int position, Long questionId, String question, String topic, String difficulty, String userAnswer,
                          boolean flagged, String status, Double score, Evaluation evaluation, String referenceAnswer, String error) {}
    public record Results(Double overallScore, Double percent, String verdict, int answered, int unanswered, int failed,
                          Double averageOnAnswered, List<MockTestMath.Group> byTopic, List<MockTestMath.Group> byDifficulty,
                          String strongestTopic, String weakestTopic, List<String> revisionTopics, int weakCount) {}
    public record TestDetail(Long id, String title, String scope, List<String> topics, String difficulty, String status,
                             String evaluationMode, int questionCount, int answeredCount, int flaggedCount, Integer timeLimitMinutes,
                             Instant startedAt, Instant deadlineAt, Long remainingSeconds, Instant submittedAt, Instant completedAt,
                             Long timeTakenSeconds, int evaluatedCount, int pendingCount, int failedCount, boolean aiAvailable,
                             Results results, List<ItemDto> items) {}
    public record TestSummary(Long id, String title, String scope, String status, int questionCount, int answeredCount,
                              Integer timeLimitMinutes, Instant startedAt, Instant submittedAt, Instant completedAt,
                              Double overallScore, Double percent) {}

    // ------------------------------------------------------------------ rows

    private record TestRow(Long id, String title, String scope, String topicIds, String difficulty, int questionCount,
                           Integer timeLimit, String status, String evaluationMode, Instant startedAt, Instant deadlineAt,
                           Instant submittedAt, Instant completedAt, Double overall) {}
    private record ItemRow(Long id, int no, Long questionId, String question, String topic, String difficulty, String answer,
                           boolean flagged, String status, Double score, String feedback, String error) {}

    private static final String TEST_COLS = "id, title, scope, topic_ids, difficulty, question_count, time_limit_minutes, status, " +
            "evaluation_mode, started_at, deadline_at, submitted_at, completed_at, overall_score";

    private static Instant ts(ResultSet rs, int col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private static TestRow mapTest(ResultSet rs, int i) throws SQLException {
        return new TestRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6),
                rs.getObject(7, Integer.class), rs.getString(8), rs.getString(9), ts(rs, 10), ts(rs, 11), ts(rs, 12), ts(rs, 13),
                rs.getObject(14, Double.class));
    }

    private TestRow row(Long id) {
        List<TestRow> r = jdbc.query("SELECT " + TEST_COLS + " FROM mock_tests WHERE id = ?", MockTestService::mapTest, id);
        if (r.isEmpty()) throw new NotFoundException("Mock test", id);
        return r.get(0);
    }

    private TestRow lockRow(Long id) {
        List<TestRow> r = jdbc.query("SELECT " + TEST_COLS + " FROM mock_tests WHERE id = ? FOR UPDATE", MockTestService::mapTest, id);
        if (r.isEmpty()) throw new NotFoundException("Mock test", id);
        return r.get(0);
    }

    private List<ItemRow> items(Long testId) {
        return jdbc.query("SELECT id, item_no, question_id, question_text, topic_name, difficulty, user_answer, flagged, status, score, feedback, error_message " +
                        "FROM mock_test_items WHERE test_id = ? ORDER BY item_no",
                (rs, i) -> new ItemRow(rs.getLong(1), rs.getInt(2), rs.getObject(3, Long.class), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getBoolean(8), rs.getString(9), rs.getObject(10, Double.class), rs.getString(11), rs.getString(12)), testId);
    }

    // ------------------------------------------------------------------ create

    public TestDetail create(CreateRequest r) {
        Long id = tx.execute(s -> createInTx(r));
        return get(id);
    }

    private record QInfo(Long id, String text, String topic, String difficulty) {}

    private Long createInTx(CreateRequest r) {
        List<Long> topicIds = r.topicIds() == null ? List.of() : r.topicIds().stream().filter(Objects::nonNull).distinct().toList();
        List<Long> chosen;
        switch (r.scope()) {
            case CUSTOM -> {
                List<Long> ids = r.questionIds() == null ? List.of() : r.questionIds().stream().filter(Objects::nonNull).distinct().toList();
                if (ids.isEmpty()) throw new BadRequestException("Choose at least one question");
                Set<Long> approved = new HashSet<>(jdbc.queryForList("SELECT id FROM questions WHERE approval_status = 'APPROVED' AND id IN ("
                        + ph(ids.size()) + ")", Long.class, ids.toArray()));
                chosen = ids.stream().filter(approved::contains).toList();
                if (chosen.isEmpty()) throw new BadRequestException("None of these questions are in the approved bank");
            }
            case TOPICS, FULL -> {
                if (r.scope() == Scope.TOPICS) {
                    if (topicIds.isEmpty()) throw new BadRequestException("Choose at least one topic");
                    Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM topics WHERE id IN (" + ph(topicIds.size()) + ")", Integer.class, topicIds.toArray());
                    if (found == null || found != topicIds.size()) throw new NotFoundException("Topic", topicIds);
                } else topicIds = List.of();
                List<MockTestMath.Cand> pool = candidates(topicIds, r.difficulty());
                if (pool.isEmpty()) throw new BadRequestException("No approved questions match this selection");
                int n = Math.max(1, Math.min(MAX_QUESTIONS, r.questionCount() == null ? DEFAULT_QUESTIONS : r.questionCount()));
                chosen = MockTestMath.pick(pool, n, ThreadLocalRandom.current()).stream().map(MockTestMath.Cand::id).toList();
            }
            default -> throw new IllegalStateException();
        }

        Map<Long, QInfo> info = new HashMap<>();
        jdbc.query("SELECT q.id, q.question_text, t.name, q.difficulty FROM questions q JOIN topics t ON t.id = q.topic_id WHERE q.id IN (" + ph(chosen.size()) + ")",
                rs -> { info.put(rs.getLong(1), new QInfo(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4))); }, chosen.toArray());
        String title = title(r, topicIds, chosen.size());
        Instant now = Instant.now();
        Instant deadline = r.timeLimitMinutes() == null ? null : now.plus(Duration.ofMinutes(r.timeLimitMinutes()));

        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        final List<Long> topicsFinal = topicIds;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO mock_tests (title, scope, topic_ids, difficulty, question_count, time_limit_minutes, " +
                    "status, started_at, deadline_at) VALUES (?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setString(2, r.scope().name());
            ps.setString(3, topicsFinal.isEmpty() ? null : topicsFinal.stream().map(String::valueOf).collect(Collectors.joining(",")));
            ps.setString(4, r.scope() == Scope.CUSTOM || r.difficulty() == null ? null : r.difficulty().name());
            ps.setInt(5, chosen.size());
            ps.setObject(6, r.timeLimitMinutes());
            ps.setTimestamp(7, Timestamp.from(now));
            ps.setTimestamp(8, deadline == null ? null : Timestamp.from(deadline));
            return ps;
        }, kh);
        Long id = Objects.requireNonNull(kh.getKey()).longValue();
        int no = 1;
        for (Long qid : chosen) {
            QInfo q = info.get(qid);
            jdbc.update("INSERT INTO mock_test_items (test_id, item_no, question_id, question_text, topic_name, difficulty, status) VALUES (?, ?, ?, ?, ?, ?, 'UNANSWERED')",
                    id, no++, qid, q.text(), q.topic(), q.difficulty());
        }
        return id;
    }

    private List<MockTestMath.Cand> candidates(List<Long> topicIds, Difficulty d) {
        Set<Long> recent = new HashSet<>(jdbc.queryForList("SELECT DISTINCT question_id FROM question_attempts WHERE attempted_at >= ?", Long.class,
                Timestamp.from(Instant.now().minus(Duration.ofDays(RECENT_DAYS)))));
        StringBuilder sql = new StringBuilder("SELECT id, topic_id, difficulty FROM questions WHERE approval_status = 'APPROVED'");
        List<Object> args = new ArrayList<>();
        if (!topicIds.isEmpty()) { sql.append(" AND topic_id IN (").append(ph(topicIds.size())).append(")"); args.addAll(topicIds); }
        if (d != null) { sql.append(" AND difficulty = ?"); args.add(d.name()); }
        return jdbc.query(sql.toString(), (rs, i) -> new MockTestMath.Cand(rs.getLong(1), rs.getLong(2), rs.getString(3), recent.contains(rs.getLong(1))), args.toArray());
    }

    public Available available(Scope scope, List<Long> topicIds, Difficulty d) {
        List<Long> t = scope == Scope.TOPICS && topicIds != null ? topicIds : List.of();
        if (scope == Scope.TOPICS && t.isEmpty()) return new Available(0);
        return new Available(candidates(t, d).size());
    }

    private String title(CreateRequest r, List<Long> topicIds, int n) {
        String custom = r.title() == null || r.title().isBlank() ? null : r.title().trim();
        if (custom != null) return custom.length() > 200 ? custom.substring(0, 200) : custom;
        String diff = r.difficulty() == null ? "" : " · " + r.difficulty().name().charAt(0) + r.difficulty().name().substring(1).toLowerCase(Locale.ROOT);
        return switch (r.scope()) {
            case FULL -> "Full mock · " + n + " questions" + diff;
            case CUSTOM -> "Custom mock · " + n + " questions";
            case TOPICS -> {
                List<String> names = topicNames(topicIds);
                String t = names.size() <= 3 ? String.join(" + ", names) : String.join(" + ", names.subList(0, 3)) + " + " + (names.size() - 3) + " more";
                yield t + " mock · " + n + " questions" + diff;
            }
        };
    }

    private List<String> topicNames(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Map<Long, String> m = new HashMap<>();
        jdbc.query("SELECT id, name FROM topics WHERE id IN (" + ph(ids.size()) + ")", rs -> { m.put(rs.getLong(1), rs.getString(2)); }, ids.toArray());
        return ids.stream().map(m::get).filter(Objects::nonNull).toList();
    }

    // ------------------------------------------------------------------ taking the test

    private boolean expired(TestRow t) {
        return "IN_PROGRESS".equals(t.status()) && t.deadlineAt() != null && Instant.now().isAfter(t.deadlineAt().plusSeconds(GRACE_SECONDS));
    }

    public SaveResult save(Long id, Long itemId, AnswerRequest r) {
        TestRow t = row(id);
        if (expired(t)) { submit(id); throw new BadRequestException("Time is up — your test was submitted"); }
        if (!"IN_PROGRESS".equals(t.status())) throw new BadRequestException("This test was already submitted");
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM mock_test_items WHERE id = ? AND test_id = ?", Integer.class, itemId, id);
        if (exists == null || exists == 0) throw new NotFoundException("Question in test", itemId);
        Instant now = Instant.now();
        if (r.answer() != null) {
            String a = r.answer().isBlank() ? null : r.answer().trim();
            jdbc.update("UPDATE mock_test_items SET user_answer = ?, status = ?, answered_at = ? WHERE id = ?",
                    a, a == null ? "UNANSWERED" : "ANSWERED", a == null ? null : Timestamp.from(now), itemId);
        }
        if (r.flagged() != null) jdbc.update("UPDATE mock_test_items SET flagged = ? WHERE id = ?", r.flagged(), itemId);
        return jdbc.query("SELECT status, flagged FROM mock_test_items WHERE id = ?",
                (rs, i) -> new SaveResult(itemId, rs.getString(1), rs.getBoolean(2), now), itemId).get(0);
    }

    /** Ends the test. Grading starts in the background (AI) or waits for self-scores (AI off). Idempotent. */
    public TestDetail submit(Long id) {
        String next = tx.execute(s -> {
            TestRow t = lockRow(id);
            if (!"IN_PROGRESS".equals(t.status())) return null;
            Integer answered = jdbc.queryForObject("SELECT COUNT(*) FROM mock_test_items WHERE test_id = ? AND status = 'ANSWERED'", Integer.class, id);
            boolean useAi = ai.available();
            String status = (answered == null || answered == 0 || useAi) ? "EVALUATING" : "AWAITING_SELF_SCORE";
            jdbc.update("UPDATE mock_tests SET status = ?, evaluation_mode = ?, submitted_at = ? WHERE id = ?",
                    status, useAi ? "AI" : "SELF", Timestamp.from(Instant.now()), id);
            return status;
        });
        if ("EVALUATING".equals(next)) evaluator.start(id);
        return detail(row(id));
    }

    public TestDetail selfScore(Long id, SelfScoreRequest r) {
        tx.executeWithoutResult(s -> {
            TestRow t = lockRow(id);
            if (!"AWAITING_SELF_SCORE".equals(t.status())) throw new BadRequestException("This test is not waiting for self-scores");
            List<Long> pending = jdbc.queryForList("SELECT id FROM mock_test_items WHERE test_id = ? AND status = 'ANSWERED'", Long.class, id);
            long missing = pending.stream().filter(p -> r.scores().get(p) == null).count();
            if (missing > 0) throw new BadRequestException("Score every answered question (" + missing + " missing)");
            Timestamp now = Timestamp.from(Instant.now());
            for (Long p : pending) {
                double sc = r.scores().get(p);
                if (sc < 0 || sc > 10 || Double.isNaN(sc)) throw new BadRequestException("Scores must be between 0 and 10");
                jdbc.update("UPDATE mock_test_items SET status = 'EVALUATED', score = ?, evaluated_at = ? WHERE id = ?", EvaluationValidator.roundHalf(sc), now, p);
            }
            jdbc.update("UPDATE mock_tests SET status = 'EVALUATING' WHERE id = ?", id);
        });
        completer.complete(id);
        return get(id);
    }

    /** Switch a self-scored test to AI grading (e.g. after adding the API key). */
    public TestDetail gradeWithAi(Long id) {
        if (!ai.available()) throw new AiExceptions.AiUnavailableException("AI is not configured on the backend");
        tx.executeWithoutResult(s -> {
            TestRow t = lockRow(id);
            if (!"AWAITING_SELF_SCORE".equals(t.status())) throw new BadRequestException("This test is not waiting for grading");
            jdbc.update("UPDATE mock_tests SET status = 'EVALUATING', evaluation_mode = 'AI' WHERE id = ?", id);
        });
        evaluator.start(id);
        return detail(row(id));
    }

    public TestDetail retryFailed(Long id) {
        if (!ai.available()) throw new AiExceptions.AiUnavailableException("AI is not configured on the backend");
        tx.executeWithoutResult(s -> {
            TestRow t = lockRow(id);
            if (!"COMPLETED".equals(t.status()) && !"EVALUATING".equals(t.status())) throw new BadRequestException("Submit the test first");
            int n = jdbc.update("UPDATE mock_test_items SET status = 'ANSWERED', error_message = NULL WHERE test_id = ? AND status = 'FAILED'", id);
            if (n == 0) throw new BadRequestException("Nothing to retry");
            jdbc.update("UPDATE mock_tests SET status = 'EVALUATING', completed_at = NULL WHERE id = ?", id);
        });
        evaluator.start(id);
        return detail(row(id));
    }

    /** New custom test from this one's questions: only the weak/unanswered ones, or all of them. */
    public TestDetail retake(Long id, RetakeRequest r) {
        TestRow t = row(id);
        if (!"COMPLETED".equals(t.status())) throw new BadRequestException("Finish this test first");
        boolean onlyWeak = r == null || r.onlyWeak() == null || r.onlyWeak();
        List<Long> ids = items(id).stream().filter(i -> i.questionId() != null)
                .filter(i -> !onlyWeak || i.answer() == null || "FAILED".equals(i.status()) || (i.score() != null && i.score() < MockTestMath.WEAK))
                .map(ItemRow::questionId).toList();
        if (ids.isEmpty()) throw new BadRequestException("No weak questions in this test — nice work!");
        String title = (onlyWeak ? "Retake weak · " : "Retake · ") + t.title();
        return create(new CreateRequest(Scope.CUSTOM, null, null, null, r == null ? null : r.timeLimitMinutes(), ids,
                title.length() > 200 ? title.substring(0, 200) : title));
    }

    public void delete(Long id) {
        row(id);
        jdbc.update("DELETE FROM mock_tests WHERE id = ?", id);
    }

    // ------------------------------------------------------------------ read

    public TestDetail get(Long id) {
        TestRow t = row(id);
        if (expired(t)) return submit(id);
        return detail(t);
    }

    public List<TestSummary> list() {
        return jdbc.query("SELECT t.id, t.title, t.scope, t.status, t.question_count, t.time_limit_minutes, t.started_at, t.submitted_at, " +
                        "t.completed_at, t.overall_score, (SELECT COUNT(*) FROM mock_test_items i WHERE i.test_id = t.id AND i.user_answer IS NOT NULL) " +
                        "FROM mock_tests t ORDER BY t.started_at DESC, t.id DESC LIMIT 30",
                (rs, i) -> {
                    Double o = rs.getObject(10, Double.class);
                    return new TestSummary(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(11),
                            rs.getObject(6, Integer.class), ts(rs, 7), ts(rs, 8), ts(rs, 9), o, o == null ? null : MockTestMath.round1(o * 10));
                });
    }

    private TestDetail detail(TestRow t) {
        List<ItemRow> items = items(t.id());
        boolean done = "COMPLETED".equals(t.status());
        boolean showReference = done || "AWAITING_SELF_SCORE".equals(t.status());
        Map<Long, String> refs = new HashMap<>();
        List<Long> qids = items.stream().map(ItemRow::questionId).filter(Objects::nonNull).toList();
        if (showReference && !qids.isEmpty())
            jdbc.query("SELECT id, answer_text FROM questions WHERE id IN (" + ph(qids.size()) + ")", rs -> { refs.put(rs.getLong(1), rs.getString(2)); }, qids.toArray());

        List<ItemDto> dtos = items.stream().map(i -> new ItemDto(i.id(), i.no(), i.questionId(), i.question(), i.topic(), i.difficulty(),
                i.answer(), i.flagged(), i.status(), done ? i.score() : null, done ? read(i.feedback()) : null,
                showReference && i.questionId() != null ? refs.get(i.questionId()) : null, done ? i.error() : null)).toList();

        int answered = (int) items.stream().filter(i -> i.answer() != null).count();
        int flagged = (int) items.stream().filter(ItemRow::flagged).count();
        int evaluated = (int) items.stream().filter(i -> "EVALUATED".equals(i.status())).count();
        int failed = (int) items.stream().filter(i -> "FAILED".equals(i.status())).count();
        int pending = "IN_PROGRESS".equals(t.status()) ? 0 : (int) items.stream().filter(i -> "ANSWERED".equals(i.status())).count();
        Long remaining = "IN_PROGRESS".equals(t.status()) && t.deadlineAt() != null
                ? Math.max(0, Duration.between(Instant.now(), t.deadlineAt()).getSeconds()) : null;
        Long taken = t.submittedAt() == null ? null : Duration.between(t.startedAt(), t.submittedAt()).getSeconds();
        List<Long> topicIds = t.topicIds() == null ? List.of()
                : Arrays.stream(t.topicIds().split(",")).filter(s -> !s.isBlank()).map(s -> Long.valueOf(s.trim())).toList();
        return new TestDetail(t.id(), t.title(), t.scope(), topicNames(topicIds), t.difficulty(), t.status(), t.evaluationMode(),
                t.questionCount(), answered, flagged, t.timeLimit(), t.startedAt(), t.deadlineAt(), remaining, t.submittedAt(), t.completedAt(),
                taken, evaluated, pending, failed, ai.available(), done ? results(items) : null, dtos);
    }

    private Results results(List<ItemRow> items) {
        List<MockTestMath.ItemScore> scores = items.stream().map(i -> new MockTestMath.ItemScore(i.topic() == null ? "General" : i.topic(),
                i.difficulty() == null ? "UNSPECIFIED" : i.difficulty(), i.score(), i.answer() != null, "FAILED".equals(i.status()))).toList();
        MockTestMath.Totals tot = MockTestMath.totals(scores);
        List<MockTestMath.Group> byTopic = MockTestMath.groups(scores, MockTestMath.ItemScore::topic, Comparator.naturalOrder());
        List<MockTestMath.Group> byDiff = MockTestMath.groups(scores, MockTestMath.ItemScore::difficulty,
                Comparator.comparingInt(MockTestMath::rank).thenComparing(Comparator.naturalOrder()));
        List<MockTestMath.Group> scored = byTopic.stream().filter(g -> g.score() != null).toList();
        String strongest = null, weakest = null;
        if (scored.size() >= 2) {
            strongest = scored.stream().max(Comparator.comparingDouble(MockTestMath.Group::score)).get().label();
            weakest = scored.stream().min(Comparator.comparingDouble(MockTestMath.Group::score)).get().label();
        }
        List<List<String>> topics = items.stream().map(i -> read(i.feedback())).filter(Objects::nonNull).map(Evaluation::revisionTopics).toList();
        int weak = (int) items.stream().filter(i -> i.answer() == null || (i.score() != null && i.score() < MockTestMath.WEAK)).count();
        return new Results(tot.overall(), tot.percent(), MockTestMath.verdict(tot.percent()), tot.answered(), tot.unanswered(), tot.failed(),
                tot.averageOnAnswered(), byTopic, byDiff, strongest, weakest, MockTestMath.topRevisionTopics(topics, 8), weak);
    }

    private Evaluation read(String s) {
        if (s == null || s.isBlank()) return null;
        try { return json.readValue(s, Evaluation.class); } catch (Exception e) { return null; }
    }

    static String ph(int n) { return String.join(",", Collections.nCopies(Math.max(1, n), "?")); }
}
