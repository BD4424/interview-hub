package com.prephub.importer.parser;

import com.prephub.common.TextNormalizer;
import com.prephub.importer.QuestionCandidate;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Maps rows of a tabular source (CSV, XLSX) to {@link QuestionCandidate}s.
 * Columns are mapped automatically from header aliases unless the user supplies an explicit mapping
 * (header -> field). Fields: question, answer, topic, tags, company, difficulty, type, round, role, job_level, source, ignore.
 */
public final class TabularMapper {
    private TabularMapper() {}

    public static final String COMPANY_DELIMITER = ";";
    public static final String TAG_DELIMITER = ",";
    public static final String IGNORE = "ignore";
    public static final List<String> FIELDS = List.of("question", "answer", "topic", "tags", "company", "difficulty",
            "type", "round", "role", "job_level", "source", IGNORE);

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("question", "question"), Map.entry("question_text", "question"), Map.entry("questions", "question"), Map.entry("q", "question"),
            Map.entry("answer", "answer"), Map.entry("model_answer", "answer"), Map.entry("expected_answer", "answer"), Map.entry("answer_text", "answer"), Map.entry("answers", "answer"), Map.entry("explanation", "answer"), Map.entry("solution", "answer"),
            Map.entry("topic", "topic"), Map.entry("primary_topic", "topic"), Map.entry("category", "topic"),
            Map.entry("tags", "tags"), Map.entry("tag", "tags"), Map.entry("keywords", "tags"),
            Map.entry("company", "company"), Map.entry("companies", "company"),
            Map.entry("difficulty", "difficulty"), Map.entry("level", "difficulty"),
            Map.entry("type", "type"), Map.entry("question_type", "type"),
            Map.entry("round", "round"), Map.entry("interview_round", "round"),
            Map.entry("role", "role"), Map.entry("job_level", "job_level"), Map.entry("seniority", "job_level"),
            Map.entry("source", "source"), Map.entry("notes", "source"), Map.entry("reference", "source"));

    public record SourceRow(String reference, List<String> cells) {}

    /** Makes header names unique ("Name", "Name (2)") and non-blank ("Column 3"). */
    public static List<String> uniqueHeaders(List<String> raw) {
        List<String> out = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < raw.size(); i++) {
            String h = TextNormalizer.cleanDisplay(raw.get(i));
            if (h == null) h = "Column " + (i + 1);
            int n = seen.merge(h.toLowerCase(Locale.ROOT), 1, Integer::sum);
            out.add(n == 1 ? h : h + " (" + n + ")");
        }
        return out;
    }

    public static Map<String, String> autoMapping(List<String> headers) {
        Map<String, String> m = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (String h : headers) {
            String key = Optional.ofNullable(TextNormalizer.normalizeName(h)).orElse("").replaceAll("[\\s-]+", "_");
            String f = ALIASES.get(key);
            if (f != null && used.add(f)) m.put(h, f); else m.put(h, IGNORE);
        }
        return m;
    }

    /** Validates a user mapping against the headers. Unknown headers or fields, or a field used twice, are errors. */
    static Map<String, String> effectiveMapping(List<String> headers, Map<String, String> override) {
        if (override == null || override.isEmpty()) return autoMapping(headers);
        Map<String, String> m = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (var e : override.entrySet()) {
            if (!headers.contains(e.getKey())) throw new IllegalArgumentException("Mapping refers to unknown column '" + e.getKey() + "'");
            String f = e.getValue() == null ? IGNORE : e.getValue().trim().toLowerCase(Locale.ROOT);
            if (!FIELDS.contains(f)) throw new IllegalArgumentException("Unknown target field '" + e.getValue() + "'");
            if (!f.equals(IGNORE) && !used.add(f)) throw new IllegalArgumentException("Two columns are mapped to '" + f + "'");
        }
        for (String h : headers) m.put(h, Optional.ofNullable(override.get(h)).map(v -> v.trim().toLowerCase(Locale.ROOT)).orElse(IGNORE));
        return m;
    }

    public static ParsedFile map(List<String> rawHeaders, List<SourceRow> rows, Map<String, String> override) {
        List<String> headers = uniqueHeaders(rawHeaders);
        Map<String, String> mapping;
        try { mapping = effectiveMapping(headers, override); }
        catch (MappingException e) { throw e; }
        catch (IllegalArgumentException e) { throw new MappingException(e.getMessage(), headers, autoMapping(headers)); }
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String f = mapping.get(headers.get(i));
            if (!IGNORE.equals(f)) idx.put(f, i);
        }
        if (!idx.containsKey("question"))
            throw new MappingException("No column is mapped to 'question'. Map one of these columns: " + String.join(", ", headers), headers, mapping);
        List<QuestionCandidate> out = new ArrayList<>();
        int blank = 0;
        for (SourceRow row : rows) {
            if (row.cells().stream().allMatch(s -> s == null || s.isBlank())) { blank++; continue; }
            if (out.size() >= ImportLimits.MAX_CANDIDATES)
                throw new IllegalArgumentException("File has more than " + ImportLimits.MAX_CANDIDATES + " rows; split it into smaller files");
            List<String> c = row.cells();
            out.add(new QuestionCandidate(
                    row.reference(),
                    TextNormalizer.cleanDisplayKeepLines(get(c, idx, "question")),
                    blankToNull(get(c, idx, "answer")),
                    TextNormalizer.cleanDisplay(get(c, idx, "topic")),
                    split(get(c, idx, "tags"), TAG_DELIMITER),
                    split(get(c, idx, "company"), COMPANY_DELIMITER),
                    TextNormalizer.cleanDisplay(get(c, idx, "difficulty")),
                    TextNormalizer.cleanDisplay(get(c, idx, "type")),
                    TextNormalizer.cleanDisplay(get(c, idx, "round")),
                    TextNormalizer.cleanDisplay(get(c, idx, "role")),
                    TextNormalizer.cleanDisplay(get(c, idx, "job_level")),
                    TextNormalizer.cleanDisplay(get(c, idx, "source"))));
        }
        List<String> notes = new ArrayList<>();
        if (blank > 0) notes.add(blank + " empty row(s) skipped");
        List<String> ignored = headers.stream().filter(h -> IGNORE.equals(mapping.get(h))).toList();
        if (!ignored.isEmpty()) notes.add("Ignored column(s): " + String.join(", ", ignored));
        return new ParsedFile(headers, mapping, out, notes);
    }

    private static String get(List<String> row, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        return (i == null || i >= row.size()) ? null : row.get(i);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** Splits, cleans and de-duplicates (by normalized form; first spelling wins). */
    public static List<String> split(String raw, String delimiter) {
        if (raw == null || raw.isBlank()) return List.of();
        LinkedHashMap<String, String> seen = new LinkedHashMap<>();
        for (String part : raw.split(Pattern.quote(delimiter))) {
            String clean = TextNormalizer.cleanDisplay(part);
            if (clean != null) seen.putIfAbsent(TextNormalizer.normalizeName(clean), clean);
        }
        return List.copyOf(seen.values());
    }
}
