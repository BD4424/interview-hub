package com.prephub.study;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Framework-free rules for mastery, learning paths, interview schedules and week comparisons (unit-tested). */
public final class StudyMath {
    private StudyMath() {}

    public static final double MASTERED = 70.0;
    public static final int MAX_PER_DAY = 25;

    /** Latest known state of one question for the current user. lastScore is null when never attempted. */
    public record QState(Long id, Double lastScore, int attempts) {}

    /**
     * Mastery % = average of (latest score / 10) over ALL questions in the set; never-practised questions count as 0,
     * so mastery rewards both coverage and quality. Null for an empty set.
     */
    public static Double mastery(Collection<QState> qs) {
        if (qs == null || qs.isEmpty()) return null;
        double sum = 0;
        for (QState q : qs) if (q.lastScore() != null) sum += Math.max(0, Math.min(10, q.lastScore())) / 10.0;
        return round1(sum / qs.size() * 100);
    }

    /** Average latest score (0-10) of practised questions only; null if none practised. */
    public static Double currentScore(Collection<QState> qs) {
        OptionalDouble a = qs.stream().filter(q -> q.lastScore() != null).mapToDouble(QState::lastScore).average();
        return a.isPresent() ? round1(a.getAsDouble()) : null;
    }

    public static int practised(Collection<QState> qs) { return (int) qs.stream().filter(q -> q.attempts() > 0).count(); }

    public enum StepStatus { MASTERED, IN_PROGRESS, NOT_STARTED, EMPTY }

    public static StepStatus status(Double mastery, int practised, int total) {
        if (total == 0) return StepStatus.EMPTY;
        if (mastery != null && mastery >= MASTERED) return StepStatus.MASTERED;
        return practised > 0 ? StepStatus.IN_PROGRESS : StepStatus.NOT_STARTED;
    }

    /** Index of the first step that has questions and is not mastered, or -1 when everything is mastered/empty. */
    public static int nextStep(List<StepStatus> statuses) {
        for (int i = 0; i < statuses.size(); i++)
            if (statuses.get(i) == StepStatus.IN_PROGRESS || statuses.get(i) == StepStatus.NOT_STARTED) return i;
        return -1;
    }

    // ------------------------------------------------------------------ interview plan

    public record Day(LocalDate date, List<Long> questionIds) {}
    public record Schedule(int daysLeft, int perDay, List<Long> todayDone, List<Long> todayPending, List<Day> upcoming) {}

    /** Days left counting today and excluding the interview day itself; 0 if the interview is today or past. */
    public static int daysLeft(LocalDate today, LocalDate interview) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(today, interview));
    }

    /**
     * Spreads the pending questions (already ordered by priority) over the days before the interview.
     * Today's list = questions already practised today (done) + enough pending ones to reach the daily load.
     * The day before the interview is kept for the leftovers only, so the last evening stays light when possible.
     */
    public static Schedule schedule(List<Long> pendingOrdered, List<Long> doneToday, LocalDate today, LocalDate interview, Integer dailyTarget) {
        int days = daysLeft(today, interview);
        List<Long> pending = new ArrayList<>(pendingOrdered);
        pending.removeAll(doneToday);
        if (days == 0) return new Schedule(0, 0, doneToday, List.of(), List.of());
        int perDay = dailyTarget != null && dailyTarget > 0
                ? Math.min(dailyTarget, MAX_PER_DAY)
                : (int) Math.max(1, Math.min(MAX_PER_DAY, Math.ceil((pending.size() + doneToday.size()) / (double) days)));
        int todayNew = Math.max(0, perDay - doneToday.size());
        List<Long> todayPending = pending.subList(0, Math.min(todayNew, pending.size()));
        List<Day> upcoming = new ArrayList<>();
        int idx = todayPending.size();
        for (int d = 1; d < days && idx < pending.size(); d++) {
            int end = Math.min(pending.size(), idx + perDay);
            upcoming.add(new Day(today.plusDays(d), List.copyOf(pending.subList(idx, end))));
            idx = end;
        }
        return new Schedule(days, perDay, List.copyOf(doneToday), List.copyOf(todayPending), upcoming);
    }

    // ------------------------------------------------------------------ weekly

    /** Topic whose average score improved most between two periods; needs data in both. Empty if none improved. */
    public static Optional<Map.Entry<String, Double>> mostImproved(Map<String, Double> previous, Map<String, Double> current) {
        String best = null;
        double delta = 0;
        for (var e : current.entrySet()) {
            Double prev = previous.get(e.getKey());
            if (prev == null || e.getValue() == null) continue;
            double d = e.getValue() - prev;
            if (d > delta + 1e-9) { delta = d; best = e.getKey(); }
        }
        return best == null ? Optional.empty() : Optional.of(Map.entry(best, round1(delta)));
    }

    public static Double pctChange(double previous, double current) {
        if (previous == 0) return current == 0 ? 0.0 : null;
        return round1((current - previous) / previous * 100);
    }

    public static double round1(double v) { return Math.round(v * 10) / 10.0; }
}
