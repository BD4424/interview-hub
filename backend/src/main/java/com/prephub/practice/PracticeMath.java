package com.prephub.practice;

import com.prephub.common.Enums.Difficulty;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Framework-free rules for adaptive difficulty, spaced revision and streaks (unit-tested). */
public final class PracticeMath {
    private PracticeMath() {}

    public static final double WEAK_SCORE = 6.0;

    /** Steps difficulty up after strong answers and down after weak ones (average of the last two scores). */
    public static Difficulty nextDifficulty(Difficulty current, List<Double> recentScores) {
        Difficulty base = current == null ? Difficulty.INTERMEDIATE : current;
        if (recentScores == null || recentScores.isEmpty()) return base;
        List<Double> last = recentScores.subList(Math.max(0, recentScores.size() - 2), recentScores.size());
        double avg = last.stream().mapToDouble(Double::doubleValue).average().orElse(5);
        int i = base.ordinal();
        if (avg >= 7.5) i = Math.min(i + 1, Difficulty.values().length - 1);
        else if (avg <= 4.5) i = Math.max(i - 1, 0);
        return Difficulty.values()[i];
    }

    /** Mock interviews dig deeper when the answer was partly right; a very weak answer moves on (it goes to revision). */
    public static boolean mockShouldFollowUp(double score, boolean hasFollowUp, int followUpDepth) {
        return hasFollowUp && followUpDepth < 1 && score >= 4.0;
    }

    /** Spaced-revision interval after the last attempt. */
    public static Duration revisionInterval(double lastScore, int attempts) {
        if (lastScore < WEAK_SCORE) return Duration.ofDays(1);
        if (lastScore < 8) return Duration.ofDays(3);
        return attempts >= 3 ? Duration.ofDays(14) : Duration.ofDays(7);
    }

    public static boolean isDue(Instant lastAttempt, double lastScore, int attempts, Instant now) {
        return lastAttempt != null && !lastAttempt.plus(revisionInterval(lastScore, attempts)).isAfter(now);
    }

    /**
     * Revision priority: previously incorrect +6, bookmarked +3, due for revision +2, never attempted +2.
     * Zero means "nothing to revise right now".
     */
    public static int revisionPriority(Integer attempts, Double lastScore, Instant lastAttempt, boolean bookmarked, Instant now) {
        int p = 0;
        boolean attempted = attempts != null && attempts > 0 && lastScore != null;
        if (attempted && lastScore < WEAK_SCORE) p += 6;
        if (bookmarked) p += 3;
        if (attempted && isDue(lastAttempt, lastScore, attempts, now)) p += 2;
        if (!attempted) p += 2;
        return p;
    }

    /** Consecutive practice days ending today, or yesterday if today has no practice yet (streak still alive). */
    public static int streak(Set<LocalDate> days, LocalDate today) {
        LocalDate d = days.contains(today) ? today : today.minusDays(1);
        int n = 0;
        while (days.contains(d)) { n++; d = d.minusDays(1); }
        return n;
    }
}
