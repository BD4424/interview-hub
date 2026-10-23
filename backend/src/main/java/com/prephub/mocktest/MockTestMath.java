package com.prephub.mocktest;

import java.util.*;
import java.util.function.Function;

/** Framework-free rules for picking and scoring mock tests (unit-tested). */
public final class MockTestMath {
    private MockTestMath() {}

    public static final double WEAK = 6.0;

    public record Cand(Long id, Long topicId, String difficulty, boolean recentlyPracticed) {}

    /**
     * Balanced pick: questions are grouped by topic and taken round-robin, so every topic in scope is covered evenly
     * (a topic with few questions simply runs out and the others fill the rest). Inside a topic the order is random,
     * with questions practised in the last few days pushed to the back. The final paper runs easy → hard.
     */
    public static List<Cand> pick(List<Cand> pool, int count, Random rnd) {
        Map<Long, List<Cand>> byTopic = new LinkedHashMap<>();
        for (Cand c : pool) byTopic.computeIfAbsent(c.topicId(), k -> new ArrayList<>()).add(c);
        List<List<Cand>> groups = new ArrayList<>();
        for (List<Cand> g : byTopic.values()) {
            List<Cand> s = new ArrayList<>(g);
            Collections.shuffle(s, rnd);
            s.sort(Comparator.comparing(Cand::recentlyPracticed));   // stable: fresh first, random within
            groups.add(s);
        }
        Collections.shuffle(groups, rnd);
        List<Cand> out = new ArrayList<>();
        boolean any = true;
        for (int round = 0; out.size() < count && any; round++) {
            any = false;
            for (List<Cand> g : groups) {
                if (round >= g.size()) continue;
                any = true;
                out.add(g.get(round));
                if (out.size() == count) break;
            }
        }
        out.sort(Comparator.comparingInt(c -> rank(c.difficulty())));
        return out;
    }

    public static int rank(String difficulty) {
        if ("BEGINNER".equals(difficulty)) return 0;
        if ("ADVANCED".equals(difficulty)) return 2;
        if ("UNSPECIFIED".equals(difficulty)) return 3;
        return 1;
    }

    public record ItemScore(String topic, String difficulty, Double score, boolean answered, boolean failed) {}
    public record Totals(Double overall, Double percent, int answered, int unanswered, int failed, Double averageOnAnswered) {}

    /**
     * Like a real paper: unanswered questions score 0. Answers whose grading failed are left out until re-graded.
     * overall is 0-10, percent 0-100; both null when nothing can be scored yet.
     */
    public static Totals totals(List<ItemScore> items) {
        int answered = 0, unanswered = 0, failed = 0, graded = 0;
        double sum = 0;
        for (ItemScore i : items) {
            if (!i.answered()) { unanswered++; continue; }
            answered++;
            if (i.failed()) { failed++; continue; }
            if (i.score() != null) { sum += i.score(); graded++; }
        }
        int denom = graded + unanswered;
        Double raw = denom == 0 ? null : sum / denom;
        return new Totals(raw == null ? null : round1(raw), raw == null ? null : round1(raw * 10), answered, unanswered, failed,
                graded == 0 ? null : round1(sum / graded));
    }

    public record Group(String label, int questions, int answered, Double score) {}

    public static List<Group> groups(List<ItemScore> items, Function<ItemScore, String> key, Comparator<String> order) {
        Map<String, List<ItemScore>> m = new TreeMap<>(order);
        for (ItemScore i : items) m.computeIfAbsent(key.apply(i), k -> new ArrayList<>()).add(i);
        List<Group> out = new ArrayList<>();
        m.forEach((label, list) -> { Totals t = totals(list); out.add(new Group(label, list.size(), t.answered(), t.overall())); });
        return out;
    }

    public static String verdict(Double percent) {
        if (percent == null) return "Not scored yet";
        if (percent >= 85) return "Interview-ready 🏆";
        if (percent >= 70) return "Strong result 💪";
        if (percent >= 50) return "Getting there — close the gaps";
        return "Needs work — revise and retake";
    }

    /** Most frequent revision topics across all evaluations (first-seen order breaks ties). */
    public static List<String> topRevisionTopics(List<List<String>> lists, int max) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> display = new HashMap<>();
        for (List<String> l : lists) for (String s : l == null ? List.<String>of() : l) {
            if (s == null || s.isBlank()) continue;
            String k = s.trim().toLowerCase(Locale.ROOT);
            counts.merge(k, 1, Integer::sum);
            display.putIfAbsent(k, s.trim());
        }
        return counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(max).map(e -> display.get(e.getKey())).toList();
    }

    public static double round1(double v) { return Math.round(v * 10) / 10.0; }
}
