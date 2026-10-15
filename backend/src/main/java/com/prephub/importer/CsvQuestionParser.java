package com.prephub.importer;

import com.prephub.common.TextNormalizer;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Header-driven CSV parser. Recognised headers (case/space-insensitive, aliases allowed):
 * question*, answer, topic, tags (comma separated), company/companies (semicolon separated),
 * difficulty, type, round, role, job_level, source.
 */
public class CsvQuestionParser {
    public static final String COMPANY_DELIMITER = ";";
    public static final String TAG_DELIMITER = ",";

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("question", "question"), Map.entry("question_text", "question"), Map.entry("q", "question"),
            Map.entry("answer", "answer"), Map.entry("explanation", "answer"),
            Map.entry("topic", "topic"), Map.entry("primary_topic", "topic"),
            Map.entry("tags", "tags"), Map.entry("tag", "tags"),
            Map.entry("company", "company"), Map.entry("companies", "company"),
            Map.entry("difficulty", "difficulty"), Map.entry("level", "difficulty"),
            Map.entry("type", "type"), Map.entry("question_type", "type"),
            Map.entry("round", "round"), Map.entry("interview_round", "round"),
            Map.entry("role", "role"), Map.entry("job_level", "job_level"),
            Map.entry("source", "source"), Map.entry("notes", "source"));

    public record ParseResult(List<String> unmappedHeaders, List<QuestionCandidate> candidates) {}

    public ParseResult parse(byte[] bytes) {
        List<List<String>> rows = CsvReader.parse(new String(bytes, StandardCharsets.UTF_8));
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV file is empty");
        List<String> header = rows.get(0);
        Map<String, Integer> idx = new HashMap<>();
        List<String> unmapped = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            String key = Optional.ofNullable(TextNormalizer.normalizeName(header.get(i))).orElse("").replace(' ', '_');
            String target = ALIASES.get(key);
            if (target != null && !idx.containsKey(target)) idx.put(target, i); else unmapped.add(header.get(i));
        }
        if (!idx.containsKey("question")) throw new IllegalArgumentException("CSV must contain a 'question' column");
        List<QuestionCandidate> out = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.stream().allMatch(s -> s == null || s.isBlank())) continue;
            out.add(new QuestionCandidate(
                    "row " + (r + 1),
                    TextNormalizer.cleanDisplayKeepLines(get(row, idx, "question")),
                    blankToNull(get(row, idx, "answer")),
                    TextNormalizer.cleanDisplay(get(row, idx, "topic")),
                    split(get(row, idx, "tags"), TAG_DELIMITER),
                    split(get(row, idx, "company"), COMPANY_DELIMITER),
                    TextNormalizer.cleanDisplay(get(row, idx, "difficulty")),
                    TextNormalizer.cleanDisplay(get(row, idx, "type")),
                    TextNormalizer.cleanDisplay(get(row, idx, "round")),
                    TextNormalizer.cleanDisplay(get(row, idx, "role")),
                    TextNormalizer.cleanDisplay(get(row, idx, "job_level")),
                    TextNormalizer.cleanDisplay(get(row, idx, "source"))));
        }
        return new ParseResult(unmapped, out);
    }

    private static String get(List<String> row, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        return (i == null || i >= row.size()) ? null : row.get(i);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** Splits, cleans and de-duplicates (by normalized form, first spelling wins). */
    public static List<String> split(String raw, String delimiter) {
        if (raw == null || raw.isBlank()) return List.of();
        LinkedHashMap<String, String> seen = new LinkedHashMap<>();
        for (String part : raw.split(java.util.regex.Pattern.quote(delimiter))) {
            String clean = TextNormalizer.cleanDisplay(part);
            if (clean != null) seen.putIfAbsent(TextNormalizer.normalizeName(clean), clean);
        }
        return List.copyOf(seen.values());
    }
}
