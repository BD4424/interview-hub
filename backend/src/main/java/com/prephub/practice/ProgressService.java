package com.prephub.practice;

import com.prephub.practice.PracticeDtos.*;
import com.prephub.practice.PracticeEnums.SessionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Progress analytics. Days are bucketed in the configured time zone (APP_TIMEZONE, default Asia/Kolkata). */
@Service
public class ProgressService {
    private final PracticeStats stats;
    private final PracticeService practice;
    private final SessionRepository sessions;
    private final BookmarkRepository bookmarks;
    private final JdbcTemplate jdbc;
    private final ZoneId zone;

    public ProgressService(PracticeStats stats, PracticeService practice, SessionRepository sessions, BookmarkRepository bookmarks,
                           JdbcTemplate jdbc, @Value("${prephub.timezone:Asia/Kolkata}") String zone) {
        this.stats = stats; this.practice = practice; this.sessions = sessions; this.bookmarks = bookmarks; this.jdbc = jdbc;
        this.zone = ZoneId.of(zone);
    }

    @Transactional(readOnly = true)
    public Progress progress() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(zone);
        Map<Long, PracticeStats.QStat> qstats = stats.questionStats(null);
        Set<Long> bookmarked = stats.bookmarkedIds();

        // streak & today
        Set<LocalDate> days = stats.allAttemptTimes().stream().map(i -> i.atZone(zone).toLocalDate()).collect(Collectors.toSet());
        int streak = PracticeMath.streak(days, today);

        // last 30 days
        Instant since = today.minusDays(29).atStartOfDay(zone).toInstant();
        List<PracticeStats.AttemptRow> recentRows = stats.attemptsSince(since);
        Map<LocalDate, List<PracticeStats.AttemptRow>> byDay = recentRows.stream().collect(Collectors.groupingBy(r -> r.at().atZone(zone).toLocalDate()));
        List<DayProgress> daily = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            List<PracticeStats.AttemptRow> rows = byDay.getOrDefault(d, List.of());
            daily.add(new DayProgress(d, rows.size(), avg(rows.stream().map(PracticeStats.AttemptRow::score).toList())));
        }
        int todayCount = byDay.getOrDefault(today, List.of()).size();
        Double avg30 = avg(recentRows.stream().map(PracticeStats.AttemptRow::score).toList());

        // per topic (all time): attempts, distinct questions, average of each question's LATEST score (current skill)
        Map<String, Integer> totals = new HashMap<>();
        jdbc.query("SELECT t.name, COUNT(q.id) FROM topics t LEFT JOIN questions q ON q.topic_id = t.id AND q.approval_status = 'APPROVED' GROUP BY t.name",
                rs -> { totals.put(rs.getString(1), rs.getInt(2)); });
        Map<Long, String> topicOf = new HashMap<>();
        if (!qstats.isEmpty())
            jdbc.query("SELECT q.id, t.name FROM questions q JOIN topics t ON t.id = q.topic_id WHERE q.id IN (" + PracticeStats.placeholders(qstats.size()) + ")",
                    rs -> { topicOf.put(rs.getLong(1), rs.getString(2)); }, qstats.keySet().toArray());
        Map<String, List<Map.Entry<Long, PracticeStats.QStat>>> byTopic = qstats.entrySet().stream()
                .filter(e -> topicOf.containsKey(e.getKey())).collect(Collectors.groupingBy(e -> topicOf.get(e.getKey())));
        List<TopicProgress> topics = byTopic.entrySet().stream().map(e -> new TopicProgress(e.getKey(),
                        e.getValue().stream().mapToInt(x -> x.getValue().attempts()).sum(), e.getValue().size(),
                        totals.getOrDefault(e.getKey(), 0), avg(e.getValue().stream().map(x -> x.getValue().lastScore()).toList())))
                .sorted(Comparator.comparing(TopicProgress::topic)).toList();
        List<TopicProgress> weak = topics.stream().filter(t -> t.averageScore() != null && t.averageScore() < PracticeMath.WEAK_SCORE)
                .sorted(Comparator.comparing(TopicProgress::averageScore)).limit(5).toList();

        int due = 0;
        for (var e : qstats.entrySet()) {
            var q = e.getValue();
            if (q.lastScore() != null && (q.lastScore() < PracticeMath.WEAK_SCORE || PracticeMath.isDue(q.lastAttempt(), q.lastScore(), q.attempts(), now))) due++;
        }

        List<RecentAttempt> recentAttempts = jdbc.query("SELECT a.question_id, q.question_text, t.name, a.score, a.attempted_at FROM question_attempts a " +
                        "JOIN questions q ON q.id = a.question_id JOIN topics t ON t.id = q.topic_id ORDER BY a.attempted_at DESC, a.id DESC LIMIT 10",
                (rs, i) -> new RecentAttempt(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getObject(4) == null ? null : rs.getDouble(4), rs.getTimestamp(5).toInstant()));

        int completed = (int) sessions.findAll().stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).count();
        int totalAttempts = qstats.values().stream().mapToInt(PracticeStats.QStat::attempts).sum();
        ProgressSummary summary = new ProgressSummary(totalAttempts, qstats.size(), avg30, streak, todayCount, due, bookmarked.size(), completed);
        return new Progress(summary, daily, topics, weak, practice.recent().stream().limit(8).toList(), recentAttempts);
    }

    static Double avg(List<Double> xs) {
        OptionalDouble a = xs.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();
        return a.isPresent() ? Math.round(a.getAsDouble() * 10) / 10.0 : null;
    }
}
