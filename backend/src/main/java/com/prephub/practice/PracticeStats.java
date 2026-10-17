package com.prephub.practice;

import com.prephub.common.Enums.Difficulty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Read-side SQL for practice: attempt statistics, bookmarks and question candidates. */
@Component
public class PracticeStats {
    private final JdbcTemplate jdbc;
    public PracticeStats(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record QStat(int attempts, Double lastScore, Double bestScore, Instant lastAttempt) {}
    public record Candidate(Long id, Difficulty difficulty, String topic) {}
    public record AttemptRow(Long questionId, Double score, Instant at, String topic) {}

    /** Per-question attempt statistics (only questions with attempts). */
    public Map<Long, QStat> questionStats(Collection<Long> onlyIds) {
        Map<Long, QStat> out = new HashMap<>();
        String where = onlyIds == null ? "" : " WHERE question_id IN (" + placeholders(onlyIds.size()) + ")";
        if (onlyIds != null && onlyIds.isEmpty()) return out;
        jdbc.query("SELECT question_id, score, attempted_at FROM question_attempts" + where + " ORDER BY attempted_at ASC, id ASC", rs -> {
            long qid = rs.getLong(1);
            Double s = rs.getObject(2) == null ? null : rs.getDouble(2);
            Instant at = rs.getTimestamp(3).toInstant();
            QStat prev = out.get(qid);
            int n = prev == null ? 1 : prev.attempts() + 1;
            Double best = prev == null ? s : (s == null ? prev.bestScore() : prev.bestScore() == null ? s : Math.max(prev.bestScore(), s));
            out.put(qid, new QStat(n, s != null ? s : prev == null ? null : prev.lastScore(), best, at));
        }, onlyIds == null ? new Object[0] : onlyIds.toArray());
        return out;
    }

    public Set<Long> bookmarkedIds() { return new HashSet<>(jdbc.queryForList("SELECT question_id FROM question_bookmarks", Long.class)); }

    /** Approved questions matching the session filter. */
    public List<Candidate> candidates(Long topicId, Long companyId, List<Long> tagIds, Difficulty difficulty) {
        StringBuilder sql = new StringBuilder("SELECT q.id, q.difficulty, t.name FROM questions q JOIN topics t ON t.id = q.topic_id WHERE q.approval_status = 'APPROVED'");
        List<Object> args = new ArrayList<>();
        if (topicId != null) { sql.append(" AND q.topic_id = ?"); args.add(topicId); }
        if (companyId != null) { sql.append(" AND EXISTS (SELECT 1 FROM question_companies qc WHERE qc.question_id = q.id AND qc.company_id = ?)"); args.add(companyId); }
        if (tagIds != null && !tagIds.isEmpty()) {
            sql.append(" AND EXISTS (SELECT 1 FROM question_tags qt WHERE qt.question_id = q.id AND qt.tag_id IN (").append(placeholders(tagIds.size())).append("))");
            args.addAll(tagIds);
        }
        if (difficulty != null) { sql.append(" AND q.difficulty = ?"); args.add(difficulty.name()); }
        return jdbc.query(sql.toString(), (rs, i) -> new Candidate(rs.getLong(1),
                rs.getString(2) == null ? null : Difficulty.valueOf(rs.getString(2)), rs.getString(3)), args.toArray());
    }

    /** Attempts since a point in time, with the question's topic. */
    public List<AttemptRow> attemptsSince(Instant since) {
        return jdbc.query("SELECT a.question_id, a.score, a.attempted_at, t.name FROM question_attempts a JOIN questions q ON q.id = a.question_id " +
                        "JOIN topics t ON t.id = q.topic_id WHERE a.attempted_at >= ? ORDER BY a.attempted_at",
                (rs, i) -> new AttemptRow(rs.getLong(1), rs.getObject(2) == null ? null : rs.getDouble(2), rs.getTimestamp(3).toInstant(), rs.getString(4)),
                Timestamp.from(since));
    }

    public List<Instant> allAttemptTimes() {
        return jdbc.query("SELECT attempted_at FROM question_attempts", (rs, i) -> rs.getTimestamp(1).toInstant());
    }

    public int companyBookmarked(Long companyId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM question_bookmarks b JOIN question_companies qc ON qc.question_id = b.question_id WHERE qc.company_id = ?", Integer.class, companyId);
        return n == null ? 0 : n;
    }

    public int companyPracticed(Long companyId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(DISTINCT a.question_id) FROM question_attempts a JOIN question_companies qc ON qc.question_id = a.question_id WHERE qc.company_id = ?", Integer.class, companyId);
        return n == null ? 0 : n;
    }

    static String placeholders(int n) { return String.join(",", Collections.nCopies(n, "?")); }
}
