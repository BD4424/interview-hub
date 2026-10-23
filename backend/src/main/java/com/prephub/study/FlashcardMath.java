package com.prephub.study;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Flashcards keep their own spaced-repetition schedule, completely separate from practice scores:
 * didn't know → 1 day, shaky → 3 days, knew it → 7 / 14 / 30 days as you keep knowing it.
 */
public final class FlashcardMath {
    private FlashcardMath() {}

    public enum Grade { KNEW, SHAKY, DIDNT }
    public record Review(Grade grade, Instant at) {}
    public record State(int reviews, Grade lastGrade, Instant lastAt, int knewStreak) {
        public static final State NEW = new State(0, null, null, 0);
    }

    /** @param chronological oldest first */
    public static State state(List<Review> chronological) {
        if (chronological == null || chronological.isEmpty()) return State.NEW;
        int streak = 0;
        for (Review r : chronological) streak = r.grade() == Grade.KNEW ? streak + 1 : 0;
        Review last = chronological.get(chronological.size() - 1);
        return new State(chronological.size(), last.grade(), last.at(), streak);
    }

    public static int intervalDays(Grade g, int knewStreak) {
        return switch (g) {
            case DIDNT -> 1;
            case SHAKY -> 3;
            case KNEW -> knewStreak <= 1 ? 7 : knewStreak == 2 ? 14 : 30;
        };
    }

    public static Instant nextDue(State s) {
        return s.reviews() == 0 ? null : s.lastAt().plus(Duration.ofDays(intervalDays(s.lastGrade(), s.knewStreak())));
    }

    public static boolean isDue(State s, Instant now) { return s.reviews() == 0 || !nextDue(s).isAfter(now); }

    /** Deck order: missed & due 4, shaky & due 3, new 2, known & due 1, not due 0. */
    public static int priority(State s, Instant now) {
        if (s.reviews() == 0) return 2;
        if (!isDue(s, now)) return 0;
        return switch (s.lastGrade()) { case DIDNT -> 4; case SHAKY -> 3; case KNEW -> 1; };
    }
}
