package com.prephub.study;

import com.prephub.study.StudyMath.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudyMathTest {
    @Test void masteryCountsUnpractisedAsZero() {
        var qs = List.of(new QState(1L, 8.0, 1), new QState(2L, 6.0, 2), new QState(3L, null, 0), new QState(4L, null, 0));
        assertThat(StudyMath.mastery(qs)).isEqualTo(35.0);
        assertThat(StudyMath.currentScore(qs)).isEqualTo(7.0);
        assertThat(StudyMath.practised(qs)).isEqualTo(2);
        assertThat(StudyMath.mastery(List.of())).isNull();
    }

    @Test void stepStatusAndNextStep() {
        assertThat(StudyMath.status(80.0, 3, 3)).isEqualTo(StepStatus.MASTERED);
        assertThat(StudyMath.status(30.0, 1, 3)).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(StudyMath.status(0.0, 0, 3)).isEqualTo(StepStatus.NOT_STARTED);
        assertThat(StudyMath.status(null, 0, 0)).isEqualTo(StepStatus.EMPTY);
        assertThat(StudyMath.nextStep(List.of(StepStatus.MASTERED, StepStatus.EMPTY, StepStatus.NOT_STARTED, StepStatus.IN_PROGRESS))).isEqualTo(2);
        assertThat(StudyMath.nextStep(List.of(StepStatus.MASTERED, StepStatus.EMPTY))).isEqualTo(-1);
    }

    @Test void scheduleSpreadsPendingOverRemainingDays() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        var s = StudyMath.schedule(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), List.of(9L), today, today.plusDays(4), null);
        assertThat(s.daysLeft()).isEqualTo(4);
        assertThat(s.perDay()).isEqualTo(2);                      // ceil((7 + 1) / 4)
        assertThat(s.todayDone()).containsExactly(9L);
        assertThat(s.todayPending()).containsExactly(1L);         // 2 per day, 1 already done
        assertThat(s.upcoming()).hasSize(3);
        assertThat(s.upcoming().get(0).date()).isEqualTo(today.plusDays(1));
        assertThat(s.upcoming().get(2).questionIds()).containsExactly(6L, 7L);
    }

    @Test void scheduleHonoursTargetAndInterviewDay() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        var s = StudyMath.schedule(List.of(1L, 2L, 3L, 4L, 5L), List.of(), today, today.plusDays(10), 3);
        assertThat(s.todayPending()).containsExactly(1L, 2L, 3L);
        assertThat(s.upcoming()).hasSize(1);
        var onDay = StudyMath.schedule(List.of(1L), List.of(), today, today, null);
        assertThat(onDay.daysLeft()).isZero();
        assertThat(onDay.todayPending()).isEmpty();
    }

    @Test void weekComparisons() {
        var imp = StudyMath.mostImproved(Map.of("Java", 5.0, "Kafka", 6.0, "SQL", 8.0), Map.of("Java", 7.5, "Kafka", 6.5, "SQL", 7.0, "Angular", 9.0));
        assertThat(imp).isPresent();
        assertThat(imp.get().getKey()).isEqualTo("Java");
        assertThat(imp.get().getValue()).isEqualTo(2.5);
        assertThat(StudyMath.mostImproved(Map.of("SQL", 8.0), Map.of("SQL", 7.0))).isEmpty();
        assertThat(StudyMath.pctChange(10, 15)).isEqualTo(50.0);
        assertThat(StudyMath.pctChange(0, 3)).isNull();
        assertThat(StudyMath.pctChange(0, 0)).isEqualTo(0.0);
    }
}
