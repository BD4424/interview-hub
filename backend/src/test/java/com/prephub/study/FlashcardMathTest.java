package com.prephub.study;

import com.prephub.study.FlashcardMath.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlashcardMathTest {
    final Instant now = Instant.parse("2026-10-21T10:00:00Z");

    @Test void intervalsGrowWhileYouKeepKnowingIt() {
        var s = FlashcardMath.state(List.of(new Review(Grade.DIDNT, now.minus(Duration.ofDays(10))), new Review(Grade.KNEW, now.minus(Duration.ofDays(9))),
                new Review(Grade.KNEW, now.minus(Duration.ofDays(1)))));
        assertThat(s.reviews()).isEqualTo(3);
        assertThat(s.knewStreak()).isEqualTo(2);
        assertThat(FlashcardMath.intervalDays(Grade.KNEW, 1)).isEqualTo(7);
        assertThat(FlashcardMath.intervalDays(Grade.KNEW, 2)).isEqualTo(14);
        assertThat(FlashcardMath.intervalDays(Grade.KNEW, 5)).isEqualTo(30);
        assertThat(FlashcardMath.intervalDays(Grade.SHAKY, 0)).isEqualTo(3);
        assertThat(FlashcardMath.isDue(s, now)).isFalse();
        assertThat(FlashcardMath.priority(s, now)).isZero();
    }

    @Test void priorities() {
        assertThat(FlashcardMath.priority(State.NEW, now)).isEqualTo(2);
        assertThat(FlashcardMath.priority(FlashcardMath.state(List.of(new Review(Grade.DIDNT, now.minus(Duration.ofDays(2))))), now)).isEqualTo(4);
        assertThat(FlashcardMath.priority(FlashcardMath.state(List.of(new Review(Grade.SHAKY, now.minus(Duration.ofDays(3))))), now)).isEqualTo(3);
        assertThat(FlashcardMath.priority(FlashcardMath.state(List.of(new Review(Grade.KNEW, now.minus(Duration.ofDays(8))))), now)).isEqualTo(1);
        assertThat(FlashcardMath.priority(FlashcardMath.state(List.of(new Review(Grade.DIDNT, now.minus(Duration.ofHours(2))))), now)).isZero();
    }
}
