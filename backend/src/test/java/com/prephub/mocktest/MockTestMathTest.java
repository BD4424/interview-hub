package com.prephub.mocktest;

import com.prephub.mocktest.MockTestMath.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MockTestMathTest {
    private static Cand c(long id, long topic, String d, boolean recent) { return new Cand(id, topic, d, recent); }

    @Test void pickBalancesTopicsAndRunsEasyToHard() {
        List<Cand> pool = new ArrayList<>();
        for (long i = 1; i <= 5; i++) pool.add(c(i, 1, i % 2 == 0 ? "ADVANCED" : "BEGINNER", false));
        pool.add(c(6, 2, "INTERMEDIATE", false));
        pool.add(c(7, 2, "ADVANCED", false));
        pool.add(c(8, 3, null, false));
        for (int seed = 0; seed < 20; seed++) {
            List<Cand> p = MockTestMath.pick(pool, 6, new Random(seed));
            Map<Long, Long> perTopic = p.stream().collect(Collectors.groupingBy(Cand::topicId, Collectors.counting()));
            assertThat(perTopic).containsEntry(2L, 2L).containsEntry(3L, 1L).containsEntry(1L, 3L);
            assertThat(p.stream().map(Cand::id).distinct().count()).isEqualTo(6);
            List<Integer> ranks = p.stream().map(x -> MockTestMath.rank(x.difficulty())).toList();
            assertThat(ranks).isSorted();
        }
        assertThat(MockTestMath.pick(pool, 50, new Random(1))).hasSize(8);
    }

    @Test void pickPrefersQuestionsNotPractisedRecently() {
        List<Cand> pool = List.of(c(1, 1, null, true), c(2, 1, null, false), c(3, 1, null, false));
        for (int seed = 0; seed < 20; seed++)
            assertThat(MockTestMath.pick(pool, 2, new Random(seed))).extracting(Cand::id).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test void unansweredScoresZeroAndFailedIsExcluded() {
        var t = MockTestMath.totals(List.of(new ItemScore("Java", "BEGINNER", 7.0, true, false), new ItemScore("Java", "BEGINNER", 8.0, true, false),
                new ItemScore("Kafka", "ADVANCED", null, false, false), new ItemScore("Kafka", "ADVANCED", null, true, true)));
        assertThat(t.overall()).isEqualTo(5.0);
        assertThat(t.percent()).isEqualTo(50.0);
        assertThat(t.answered()).isEqualTo(3);
        assertThat(t.unanswered()).isEqualTo(1);
        assertThat(t.failed()).isEqualTo(1);
        assertThat(t.averageOnAnswered()).isEqualTo(7.5);
        assertThat(MockTestMath.totals(List.of(new ItemScore("A", null, null, true, true))).overall()).isNull();
    }

    @Test void groupsVerdictAndRevisionTopics() {
        var g = MockTestMath.groups(List.of(new ItemScore("Kafka", "ADVANCED", 4.0, true, false), new ItemScore("Java", "BEGINNER", 8.0, true, false),
                new ItemScore("Java", "INTERMEDIATE", null, false, false)), ItemScore::topic, Comparator.naturalOrder());
        assertThat(g).extracting(Group::label).containsExactly("Java", "Kafka");
        assertThat(g.get(0).score()).isEqualTo(4.0);
        assertThat(g.get(0).answered()).isEqualTo(1);
        assertThat(MockTestMath.verdict(90.0)).startsWith("Interview-ready");
        assertThat(MockTestMath.verdict(null)).isEqualTo("Not scored yet");
        assertThat(MockTestMath.topRevisionTopics(List.of(List.of("JVM", "GC"), List.of("gc ", "Locks"), List.of("GC")), 2))
                .containsExactly("GC", "JVM");
    }
}
