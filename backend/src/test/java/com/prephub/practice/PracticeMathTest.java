package com.prephub.practice;

import com.prephub.common.Enums.Difficulty;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeMathTest {
    @Test void adaptiveDifficulty() {
        assertThat(PracticeMath.nextDifficulty(Difficulty.INTERMEDIATE, List.of(8.0, 9.0))).isEqualTo(Difficulty.ADVANCED);
        assertThat(PracticeMath.nextDifficulty(Difficulty.ADVANCED, List.of(9.0, 9.0))).isEqualTo(Difficulty.ADVANCED);
        assertThat(PracticeMath.nextDifficulty(Difficulty.INTERMEDIATE, List.of(9.0, 3.0, 4.0))).isEqualTo(Difficulty.BEGINNER);
        assertThat(PracticeMath.nextDifficulty(null, List.of(6.0))).isEqualTo(Difficulty.INTERMEDIATE);
    }

    @Test void mockFollowUpRule() {
        assertThat(PracticeMath.mockShouldFollowUp(6, true, 0)).isTrue();
        assertThat(PracticeMath.mockShouldFollowUp(2, true, 0)).isFalse();
        assertThat(PracticeMath.mockShouldFollowUp(6, true, 1)).isFalse();
        assertThat(PracticeMath.mockShouldFollowUp(6, false, 0)).isFalse();
    }

    @Test void revisionPriorities() {
        Instant now = Instant.parse("2026-10-20T10:00:00Z");
        int wrong = PracticeMath.revisionPriority(1, 3.0, now.minus(Duration.ofHours(1)), false, now);
        int bookmarkedGood = PracticeMath.revisionPriority(1, 9.0, now.minus(Duration.ofHours(1)), true, now);
        int never = PracticeMath.revisionPriority(0, null, null, false, now);
        int masteredNotDue = PracticeMath.revisionPriority(3, 9.0, now.minus(Duration.ofDays(2)), false, now);
        int masteredDue = PracticeMath.revisionPriority(1, 9.0, now.minus(Duration.ofDays(8)), false, now);
        assertThat(wrong).isEqualTo(6);
        assertThat(bookmarkedGood).isEqualTo(3);
        assertThat(never).isEqualTo(2);
        assertThat(masteredNotDue).isZero();
        assertThat(masteredDue).isEqualTo(2);
        assertThat(PracticeMath.revisionPriority(1, 3.0, now.minus(Duration.ofDays(2)), true, now)).isEqualTo(11);
    }

    @Test void streak() {
        LocalDate t = LocalDate.of(2026, 10, 20);
        assertThat(PracticeMath.streak(Set.of(t, t.minusDays(1), t.minusDays(2), t.minusDays(4)), t)).isEqualTo(3);
        assertThat(PracticeMath.streak(Set.of(t.minusDays(1), t.minusDays(2)), t)).isEqualTo(2);
        assertThat(PracticeMath.streak(Set.of(t.minusDays(2)), t)).isZero();
    }
}
