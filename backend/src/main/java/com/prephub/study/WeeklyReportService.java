package com.prephub.study;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/** This week (Monday → now, in APP_TIMEZONE) compared with the whole of last week. */
@Service
public class WeeklyReportService {
    private final JdbcTemplate jdbc;
    private final ZoneId zone;

    public WeeklyReportService(JdbcTemplate jdbc, @Value("${prephub.timezone:Asia/Kolkata}") String zone) {
        this.jdbc = jdbc; this.zone = ZoneId.of(zone);
    }

    public record Period(LocalDate from, LocalDate to, int answers, int questions, int activeDays, Double averageScore, int flashcards) {}
    public record Improved(String topic, double delta, Double previous, Double current) {}
    public record Weak(Long questionId, String question, String topic, double lastScore, Instant lastAttempt) {}
    public record WeeklyReport(Period thisWeek, Period lastWeek, Double answersChangePct, Double scoreChange,
                               Improved mostImproved, List<Weak> weakest, boolean showAsMondaySummary) {}

    private record A(Long questionId, Double score, Instant at, String topic, String mode) {}

    @Transactional(readOnly = true)
    public WeeklyReport report() {
        LocalDate today = LocalDate.now(zone);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate prevMonday = monday.minusWeeks(1);
        Instant thisStart = monday.atStartOfDay(zone).toInstant(), prevStart = prevMonday.atStartOfDay(zone).toInstant();

        List<A> rows = jdbc.query("SELECT a.question_id, a.score, a.attempted_at, t.name, a.mode FROM question_attempts a " +
                        "JOIN questions q ON q.id = a.question_id JOIN topics t ON t.id = q.topic_id WHERE a.attempted_at >= ?",
                (rs, i) -> new A(rs.getLong(1), rs.getObject(2) == null ? null : rs.getDouble(2), rs.getTimestamp(3).toInstant(), rs.getString(4), rs.getString(5)),
                Timestamp.from(prevStart));
        List<A> cur = rows.stream().filter(a -> !a.at().isBefore(thisStart)).toList();
        List<A> prev = rows.stream().filter(a -> a.at().isBefore(thisStart)).toList();
        Period pc = period(cur, monday, today), pp = period(prev, prevMonday, monday.minusDays(1));

        var improved = StudyMath.mostImproved(topicAvg(prev), topicAvg(cur))
                .map(e -> new Improved(e.getKey(), e.getValue(), topicAvg(prev).get(e.getKey()), topicAvg(cur).get(e.getKey()))).orElse(null);

        // weakest: latest score per question (all time), lowest first
        Map<Long, Weak> latest = new LinkedHashMap<>();
        jdbc.query("SELECT a.question_id, q.question_text, t.name, a.score, a.attempted_at FROM question_attempts a JOIN questions q ON q.id = a.question_id " +
                        "JOIN topics t ON t.id = q.topic_id WHERE a.score IS NOT NULL AND q.approval_status = 'APPROVED' ORDER BY a.attempted_at, a.id",
                rs -> { latest.put(rs.getLong(1), new Weak(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getTimestamp(5).toInstant())); });
        List<Weak> weakest = latest.values().stream().filter(w -> w.lastScore() < 7)
                .sorted(Comparator.comparingDouble(Weak::lastScore).thenComparing(Weak::lastAttempt)).limit(5).toList();

        Double scoreChange = pc.averageScore() != null && pp.averageScore() != null ? StudyMath.round1(pc.averageScore() - pp.averageScore()) : null;
        boolean monday1 = today.getDayOfWeek() == DayOfWeek.MONDAY || today.getDayOfWeek() == DayOfWeek.TUESDAY;
        return new WeeklyReport(pc, pp, StudyMath.pctChange(pp.answers(), pc.answers()), scoreChange, improved, weakest, monday1);
    }

    private Period period(List<A> as, LocalDate from, LocalDate to) {
        OptionalDouble avg = as.stream().filter(a -> a.score() != null).mapToDouble(A::score).average();
        return new Period(from, to, as.size(), (int) as.stream().map(A::questionId).distinct().count(),
                (int) as.stream().map(a -> a.at().atZone(zone).toLocalDate()).distinct().count(),
                avg.isPresent() ? StudyMath.round1(avg.getAsDouble()) : null,
                (int) as.stream().filter(a -> "FLASHCARD".equals(a.mode())).count());
    }

    private static Map<String, Double> topicAvg(List<A> as) {
        return as.stream().filter(a -> a.score() != null).collect(Collectors.groupingBy(A::topic,
                Collectors.collectingAndThen(Collectors.averagingDouble(A::score), StudyMath::round1)));
    }
}
