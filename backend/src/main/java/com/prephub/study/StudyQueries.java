package com.prephub.study;

import com.prephub.practice.PracticeMath;
import com.prephub.practice.PracticeStats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Shared read-side SQL for the study features. */
@Component
public class StudyQueries {
    private final JdbcTemplate jdbc;
    private final PracticeStats stats;

    public StudyQueries(JdbcTemplate jdbc, PracticeStats stats) { this.jdbc = jdbc; this.stats = stats; }

    public record QRow(Long id, String text, String answer, Long topicId, String topic, String difficulty) {}

    /** A question joined with the user's practice state. */
    public record QView(QRow q, Double lastScore, Double bestScore, int attempts, Instant lastAttempt, boolean bookmarked, int priority) {
        public StudyMath.QState state() { return new StudyMath.QState(q.id(), lastScore, attempts); }
    }

    /** Approved questions matching all given filters (null/empty filter = no restriction). */
    public List<QRow> questions(Collection<Long> topicIds, Long companyId, Collection<Long> tagIds) {
        StringBuilder sql = new StringBuilder("SELECT q.id, q.question_text, q.answer_text, q.topic_id, t.name, q.difficulty FROM questions q " +
                "JOIN topics t ON t.id = q.topic_id WHERE q.approval_status = 'APPROVED'");
        List<Object> args = new ArrayList<>();
        if (topicIds != null && !topicIds.isEmpty()) { sql.append(" AND q.topic_id IN (").append(ph(topicIds.size())).append(")"); args.addAll(topicIds); }
        if (companyId != null) { sql.append(" AND EXISTS (SELECT 1 FROM question_companies qc WHERE qc.question_id = q.id AND qc.company_id = ?)"); args.add(companyId); }
        if (tagIds != null && !tagIds.isEmpty()) {
            sql.append(" AND EXISTS (SELECT 1 FROM question_tags qt WHERE qt.question_id = q.id AND qt.tag_id IN (").append(ph(tagIds.size())).append("))");
            args.addAll(tagIds);
        }
        sql.append(" ORDER BY q.id");
        return jdbc.query(sql.toString(), (rs, i) -> new QRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5), rs.getString(6)), args.toArray());
    }

    /** Adds practice state and revision priority (same rules as Smart revision). */
    public List<QView> withState(List<QRow> rows) {
        Map<Long, PracticeStats.QStat> st = stats.questionStats(null);
        Set<Long> bm = stats.bookmarkedIds();
        Instant now = Instant.now();
        List<QView> out = new ArrayList<>(rows.size());
        for (QRow r : rows) {
            PracticeStats.QStat s = st.get(r.id());
            int attempts = s == null ? 0 : s.attempts();
            Double last = s == null ? null : s.lastScore();
            Instant at = s == null ? null : s.lastAttempt();
            boolean b = bm.contains(r.id());
            out.add(new QView(r, last, s == null ? null : s.bestScore(), attempts, at, b, PracticeMath.revisionPriority(attempts, last, at, b, now)));
        }
        return out;
    }

    /** Sorts by revision priority, weakest first, never-practised before mastered; stable by id. */
    public static List<QView> byPriority(List<QView> views) {
        List<QView> out = new ArrayList<>(views);
        out.sort(Comparator.comparingInt(QView::priority).reversed()
                .thenComparing(v -> v.lastScore() == null ? -1.0 : v.lastScore())
                .thenComparing(v -> v.q().id()));
        return out;
    }

    public Set<Long> attemptedSince(Instant since) {
        return new HashSet<>(jdbc.queryForList("SELECT DISTINCT question_id FROM question_attempts WHERE attempted_at >= ?", Long.class, Timestamp.from(since)));
    }

    public Map<Long, List<String>> tagsOf(Collection<Long> questionIds) {
        Map<Long, List<String>> out = new HashMap<>();
        if (questionIds.isEmpty()) return out;
        jdbc.query("SELECT qt.question_id, t.name FROM question_tags qt JOIN tags t ON t.id = qt.tag_id WHERE qt.question_id IN (" + ph(questionIds.size()) + ") ORDER BY t.name",
                rs -> { out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2)); }, questionIds.toArray());
        return out;
    }

    public static String ph(int n) { return String.join(",", Collections.nCopies(Math.max(1, n), "?")); }
}
