package com.prephub.ai;

import com.prephub.common.Enums;
import com.prephub.common.Enums.Difficulty;
import com.prephub.common.TextNormalizer;

import java.util.*;

/**
 * Schema validation for AI follow-up output. Framework-free so it is unit-testable. Anything the model returns is
 * untrusted: the parent id is never taken from the model, text is normalised, sizes are bounded and items that repeat
 * the base question, earlier session questions or each other are rejected.
 */
public final class FollowUpValidator {
    private FollowUpValidator() {}

    public static final int MIN_QUESTION = 10, MAX_QUESTION = 1000, MAX_POINTS = 10, MAX_POINT = 500, MAX_TAGS = 8, MAX_TAG = 60, MAX_REASON = 500;

    /** Shape the model is asked to produce. */
    public record RawFollowUp(String question, List<String> expectedAnswerPoints, String difficulty, List<String> tags, String reason) {}
    public record RawBatch(List<RawFollowUp> followUps) {}

    public record ValidFollowUp(String question, String fingerprint, List<String> expectedAnswerPoints,
                                Difficulty difficulty, List<String> tags, String reason) {}
    public record Outcome(List<ValidFollowUp> valid, List<String> rejected) {}

    public static Outcome validate(RawBatch batch, Difficulty fallback, Collection<String> excludedQuestions, int max) {
        List<ValidFollowUp> valid = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        if (batch == null || batch.followUps() == null || batch.followUps().isEmpty()) {
            rejected.add("Model returned no follow-ups");
            return new Outcome(valid, rejected);
        }
        Set<String> seen = new HashSet<>();
        for (String q : excludedQuestions == null ? List.<String>of() : excludedQuestions) {
            String fp = TextNormalizer.fingerprint(q);
            if (fp != null) seen.add(fp);
        }
        int i = 0;
        for (RawFollowUp raw : batch.followUps()) {
            i++;
            try {
                ValidFollowUp v = validateOne(raw, fallback);
                if (!seen.add(v.fingerprint())) { rejected.add("#" + i + ": repeats the base question, an earlier question or another follow-up"); continue; }
                if (valid.size() >= max) { rejected.add("#" + i + ": more items than requested"); continue; }
                valid.add(v);
            } catch (IllegalArgumentException e) {
                rejected.add("#" + i + ": " + e.getMessage());
            }
        }
        return new Outcome(valid, rejected);
    }

    /** @throws IllegalArgumentException describing the first schema violation */
    public static ValidFollowUp validateOne(RawFollowUp raw, Difficulty fallback) {
        if (raw == null) throw new IllegalArgumentException("item is null");
        String q = TextNormalizer.cleanDisplayKeepLines(raw.question());
        if (q == null) throw new IllegalArgumentException("question is missing");
        if (q.length() < MIN_QUESTION) throw new IllegalArgumentException("question is too short");
        if (q.length() > MAX_QUESTION) throw new IllegalArgumentException("question is too long");

        List<String> points = new ArrayList<>();
        for (String p : raw.expectedAnswerPoints() == null ? List.<String>of() : raw.expectedAnswerPoints()) {
            String c = TextNormalizer.cleanDisplay(p);
            if (c == null) continue;
            if (c.length() > MAX_POINT) throw new IllegalArgumentException("an expected answer point is too long");
            points.add(c);
        }
        if (points.isEmpty()) throw new IllegalArgumentException("expectedAnswerPoints is empty");
        if (points.size() > MAX_POINTS) points = points.subList(0, MAX_POINTS);

        Difficulty d = fallback;
        if (raw.difficulty() != null && !raw.difficulty().isBlank()) {
            d = Enums.parseLenient(Difficulty.class, raw.difficulty());
            if (d == null) throw new IllegalArgumentException("invalid difficulty '" + raw.difficulty() + "'");
        }

        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        for (String t : raw.tags() == null ? List.<String>of() : raw.tags()) {
            String c = TextNormalizer.cleanDisplay(t);
            if (c == null || c.length() > MAX_TAG) continue;
            tags.putIfAbsent(TextNormalizer.normalizeName(c), c);
            if (tags.size() == MAX_TAGS) break;
        }

        String reason = TextNormalizer.cleanDisplay(raw.reason());
        if (reason == null) throw new IllegalArgumentException("reason is missing");
        if (reason.length() > MAX_REASON) reason = reason.substring(0, MAX_REASON);

        return new ValidFollowUp(q, TextNormalizer.fingerprint(q), List.copyOf(points), d, List.copyOf(tags.values()), reason);
    }

    /** Extracts the JSON object from a model reply that may be wrapped in Markdown fences or prose. */
    public static String extractJsonObject(String reply) {
        if (reply == null) throw new IllegalArgumentException("empty reply");
        String s = reply.trim();
        int start = s.indexOf('{'), end = s.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("reply contains no JSON object");
        return s.substring(start, end + 1);
    }
}
