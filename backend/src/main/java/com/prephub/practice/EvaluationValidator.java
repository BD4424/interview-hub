package com.prephub.practice;

import com.prephub.common.TextNormalizer;

import java.util.*;

/**
 * Schema validation for AI answer evaluations. Framework-free; everything the model returns is treated as untrusted.
 * The overall score must be a number in [0, 10]; out-of-range or missing scores reject the reply (no silent clamping).
 */
public final class EvaluationValidator {
    private EvaluationValidator() {}

    public static final int MAX_ITEMS = 10, MAX_ITEM = 500, MAX_IMPROVED = 8000;

    public record RawRubric(Double technicalCorrectness, Double conceptualUnderstanding, Double completeness,
                            Double practicalApplication, Double edgeCasesAndTradeoffs, Double clarity) {}
    public record RawFollowUp(String question, List<String> expectedAnswerPoints) {}
    public record RawEvaluation(Double score, RawRubric rubric, String summary, List<String> correctPoints,
                                List<String> missedPoints, List<String> incorrectStatements, String improvedAnswer,
                                List<String> revisionTopics, RawFollowUp followUpQuestion) {}

    public record Rubric(Double technicalCorrectness, Double conceptualUnderstanding, Double completeness,
                         Double practicalApplication, Double edgeCasesAndTradeoffs, Double clarity) {}
    public record FollowUp(String question, List<String> expectedAnswerPoints) {}
    public record Evaluation(double score, Rubric rubric, String summary, List<String> correctPoints, List<String> missedPoints,
                             List<String> incorrectStatements, String improvedAnswer, List<String> revisionTopics,
                             FollowUp followUpQuestion, String model) {}

    /** @throws IllegalArgumentException describing the first schema violation */
    public static Evaluation validate(RawEvaluation r, String model) {
        if (r == null) throw new IllegalArgumentException("evaluation is missing");
        if (r.score() == null || r.score().isNaN()) throw new IllegalArgumentException("score is missing");
        if (r.score() < 0 || r.score() > 10) throw new IllegalArgumentException("score " + r.score() + " is outside 0-10");
        String improved = TextNormalizer.cleanDisplayKeepLines(r.improvedAnswer());
        if (improved == null) throw new IllegalArgumentException("improvedAnswer is missing");
        if (improved.length() > MAX_IMPROVED) improved = improved.substring(0, MAX_IMPROVED);
        Rubric rubric = r.rubric() == null ? null : new Rubric(dim(r.rubric().technicalCorrectness()), dim(r.rubric().conceptualUnderstanding()),
                dim(r.rubric().completeness()), dim(r.rubric().practicalApplication()), dim(r.rubric().edgeCasesAndTradeoffs()), dim(r.rubric().clarity()));
        String summary = TextNormalizer.cleanDisplay(r.summary());
        if (summary != null && summary.length() > MAX_ITEM) summary = summary.substring(0, MAX_ITEM);
        return new Evaluation(roundHalf(r.score()), rubric, summary, items(r.correctPoints()), items(r.missedPoints()),
                items(r.incorrectStatements()), improved, items(r.revisionTopics()), followUp(r.followUpQuestion()), model);
    }

    static FollowUp followUp(RawFollowUp f) {
        if (f == null) return null;
        String q = TextNormalizer.cleanDisplayKeepLines(f.question());
        if (q == null || q.length() < 10 || q.length() > 1000) return null; // optional: drop rather than fail
        List<String> pts = items(f.expectedAnswerPoints());
        return new FollowUp(q, pts.size() > 8 ? pts.subList(0, 8) : pts);
    }

    private static Double dim(Double d) {
        if (d == null || d.isNaN()) return null;
        if (d < 0 || d > 10) throw new IllegalArgumentException("rubric value " + d + " is outside 0-10");
        return roundHalf(d);
    }

    public static double roundHalf(double d) { return Math.round(d * 2) / 2.0; }

    private static List<String> items(List<String> in) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String s : in == null ? List.<String>of() : in) {
            String c = TextNormalizer.cleanDisplay(s);
            if (c == null) continue;
            if (c.length() > MAX_ITEM) c = c.substring(0, MAX_ITEM) + "…";
            out.putIfAbsent(TextNormalizer.normalizeName(c), c);
            if (out.size() == MAX_ITEMS) break;
        }
        return List.copyOf(out.values());
    }
}
