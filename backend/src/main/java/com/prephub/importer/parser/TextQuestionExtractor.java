package com.prephub.importer.parser;

import com.prephub.common.TextNormalizer;
import com.prephub.importer.QuestionCandidate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts questions (and optional answers/metadata) from line-oriented text. Shared by TXT, Markdown, DOCX and PDF.
 *
 * <p><b>Explicit mode</b> (used when any line starts with a question marker such as {@code Q:}, {@code Q1.},
 * {@code Question 3:}): only marked lines start questions; {@code A:}/{@code Answer:} starts the answer; text after a
 * blank line following a question is also treated as its answer.
 *
 * <p><b>Heuristic mode</b> (no markers): a line starts a question when it is
 * <ul>
 *   <li>a Markdown heading that ends with '?' or starts with a question word (other headings are section names),</li>
 *   <li>a top-level numbered/bulleted item that ends with '?' or starts with a question word,</li>
 *   <li>a plain line (≤ 300 chars) ending with '?',</li>
 *   <li>a short plain line after a blank line starting with an imperative such as "Explain"/"Describe"/"Compare".</li>
 * </ul>
 * A question wrapped over up to 3 lines (common in PDFs) is joined when a following line completes it with '?'.
 *
 * <p>In both modes, lines directly under a question of the form {@code Tags: a, b}, {@code Company: X; Y},
 * {@code Difficulty: ...}, {@code Topic: ...}, {@code Type: ...}, {@code Round: ...}, {@code Role: ...},
 * {@code Job level: ...} are read as metadata. Code fences are never interpreted. Text before the first question is
 * ignored and reported. Nothing (companies in particular) is inferred from question wording.
 */
public final class TextQuestionExtractor {
    private TextQuestionExtractor() {}

    public record SourceLine(String reference, String text) {}

    private static final String SEP = "(?::|[.)\\-](?=\\s|$))";
    static final Pattern Q_MARKER = Pattern.compile("^\\s*[*_]{0,2}(?:q|question)\\s*(?:#?\\d{1,4})?\\s*" + SEP + "\\s*[*_]{0,2}\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    static final Pattern A_MARKER = Pattern.compile("^\\s*[*_]{0,2}(?:a|ans|answer)\\s*(?:#?\\d{1,4})?\\s*" + SEP + "\\s*[*_]{0,2}\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    static final Pattern META = Pattern.compile("^\\s*[*_]{0,2}(tags?|company|companies|difficulty|level|topic|type|question type|round|interview round|role|job level|source)\\s*[*_]{0,2}\\s*:\\s*[*_]{0,2}\\s*(.+?)\\s*[*_]{0,2}\\s*$", Pattern.CASE_INSENSITIVE);
    static final Pattern HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    static final Pattern LIST_ITEM = Pattern.compile("^(\\s?)(?:\\d{1,3}[.)]|[-*+•])\\s+(.*)$");
    static final Pattern QUESTION_WORD = Pattern.compile("^(?:what|why|how|when|where|which|who|can|could|is|are|does|do|did|should|would|will|explain|describe|define|compare|differentiate|distinguish|write|implement|design|list|discuss|tell|give|walk)\\b.*", Pattern.CASE_INSENSITIVE);
    static final Pattern IMPERATIVE = Pattern.compile("^(?:explain|describe|define|compare|differentiate|distinguish|write|implement|design|discuss)\\b.*", Pattern.CASE_INSENSITIVE);

    public record Result(List<QuestionCandidate> candidates, List<String> notes) {}

    private enum State { NONE, QUESTION, ANSWER }

    private static final class Builder {
        final String ref;
        final StringBuilder question = new StringBuilder();
        final List<String> answer = new ArrayList<>();
        final Map<String, String> meta = new HashMap<>();
        Builder(String ref, String q) { this.ref = ref; this.question.append(q); }
    }

    public static Result extract(List<SourceLine> lines) {
        boolean markerMode = lines.stream().anyMatch(l -> Q_MARKER.matcher(headingText(l.text())).matches());
        List<QuestionCandidate> out = new ArrayList<>();
        Builder cur = null;
        State state = State.NONE;
        boolean questionClosed = false, inFence = false, prevBlank = true;
        String section = null;
        int preamble = 0;

        for (int i = 0; i < lines.size(); i++) {
            String raw = stripTrailing(lines.get(i).text());
            String t = raw.trim();
            String ref = lines.get(i).reference() + (section != null ? " (§ " + section + ")" : "");

            if (t.startsWith("```") || t.startsWith("~~~")) {
                inFence = !inFence;
                if (cur != null) { cur.answer.add(raw); state = State.ANSWER; } else preamble++;
                prevBlank = false;
                continue;
            }
            if (inFence) {
                if (cur != null) cur.answer.add(raw); else preamble++;
                continue;
            }
            if (t.isEmpty()) {
                if (state == State.QUESTION) questionClosed = true;
                if (state == State.ANSWER && cur != null && !cur.answer.isEmpty()) cur.answer.add("");
                prevBlank = true;
                continue;
            }

            Matcher h = HEADING.matcher(raw);
            boolean isHeading = h.matches();
            String content = isHeading ? h.group(2) : t;

            // 1. explicit question marker
            if (markerMode) {
                Matcher q = Q_MARKER.matcher(content);
                if (q.matches()) {
                    flush(cur, out);
                    cur = new Builder(ref, clean(q.group(1)));
                    state = State.QUESTION; questionClosed = false; prevBlank = false;
                    continue;
                }
            }
            // 2. headings
            if (isHeading) {
                if (!markerMode && looksLikeQuestion(content)) {
                    flush(cur, out);
                    cur = new Builder(ref, clean(content));
                    i = absorbContinuation(lines, i, cur);
                } else {
                    flush(cur, out);
                    cur = null;
                    section = TextNormalizer.cleanDisplay(content);
                }
                state = cur == null ? State.NONE : State.QUESTION;
                questionClosed = false; prevBlank = false;
                continue;
            }
            // 3. explicit answer marker
            if (markerMode && cur != null && state != State.ANSWER) {
                Matcher a = A_MARKER.matcher(t);
                if (a.matches()) {
                    state = State.ANSWER;
                    if (!a.group(1).isBlank()) cur.answer.add(a.group(1));
                    prevBlank = false;
                    continue;
                }
            }
            // 4. metadata directly under a question
            if (cur != null && state == State.QUESTION) {
                Matcher m = META.matcher(t);
                if (m.matches()) {
                    cur.meta.put(metaKey(m.group(1)), m.group(2));
                    prevBlank = false;
                    continue;
                }
            }
            // 5. heuristic question detection
            if (!markerMode) {
                String qText = null;
                Matcher li = LIST_ITEM.matcher(raw);
                if (li.matches()) {
                    String item = li.group(2).trim();
                    if (looksLikeQuestion(item)) qText = item;
                } else if (t.length() <= 300 && endsWithQuestionMark(t)) {
                    qText = t;
                } else if ((prevBlank || cur == null) && t.length() <= 250 && IMPERATIVE.matcher(t).matches()
                        && !t.endsWith(".") && !t.endsWith(":")) {
                    qText = t;
                }
                if (qText != null) {
                    flush(cur, out);
                    cur = new Builder(ref, clean(qText));
                    i = absorbContinuation(lines, i, cur);
                    state = State.QUESTION; questionClosed = false; prevBlank = false;
                    continue;
                }
            }
            // 6. continuation / answer / preamble
            if (cur == null) { preamble++; prevBlank = false; continue; }
            if (markerMode && state == State.QUESTION && !questionClosed) {
                cur.question.append(' ').append(clean(t));
            } else {
                cur.answer.add(raw);
                state = State.ANSWER;
            }
            prevBlank = false;
        }
        flush(cur, out);
        if (out.size() > ImportLimits.MAX_CANDIDATES)
            throw new IllegalArgumentException("File has more than " + ImportLimits.MAX_CANDIDATES + " questions; split it into smaller files");

        List<String> notes = new ArrayList<>();
        notes.add(markerMode
                ? "Explicit mode: questions start at Q:/Question: markers, answers at A:/Answer: markers"
                : "Heuristic mode (no Q:/A: markers found): questions detected from '?' endings, question words and headings. Review the preview; add Q:/A: markers to the file for exact control");
        if (preamble > 0) notes.add(preamble + " line(s) before the first question were ignored");
        return new Result(out, notes);
    }

    /** Joins up to 2 following lines into a question that is wrapped over several lines and only ends with '?' later. */
    private static int absorbContinuation(List<SourceLine> lines, int i, Builder b) {
        if (endsWithQuestionMark(b.question.toString())) return i;
        for (int j = i + 1; j <= Math.min(i + 2, lines.size() - 1); j++) {
            String t = lines.get(j).text().trim();
            if (t.isEmpty() || HEADING.matcher(lines.get(j).text()).matches() || LIST_ITEM.matcher(lines.get(j).text()).matches()
                    || META.matcher(t).matches() || Q_MARKER.matcher(t).matches() || t.startsWith("```")) return i;
            if (endsWithQuestionMark(t)) {
                for (int k = i + 1; k <= j; k++) b.question.append(' ').append(clean(lines.get(k).text().trim()));
                return j;
            }
        }
        return i;
    }

    private static void flush(Builder b, List<QuestionCandidate> out) {
        if (b == null) return;
        List<String> ans = new ArrayList<>(b.answer);
        while (!ans.isEmpty() && ans.get(ans.size() - 1).isBlank()) ans.remove(ans.size() - 1);
        while (!ans.isEmpty() && ans.get(0).isBlank()) ans.remove(0);
        String answer = ans.isEmpty() ? null : stripCommonIndent(ans);
        Map<String, String> m = b.meta;
        out.add(new QuestionCandidate(
                b.ref,
                TextNormalizer.cleanDisplayKeepLines(b.question.toString()),
                answer,
                TextNormalizer.cleanDisplay(m.get("topic")),
                TabularMapper.split(m.get("tags"), TabularMapper.TAG_DELIMITER),
                TabularMapper.split(m.get("company"), TabularMapper.COMPANY_DELIMITER),
                TextNormalizer.cleanDisplay(m.get("difficulty")),
                TextNormalizer.cleanDisplay(m.get("type")),
                TextNormalizer.cleanDisplay(m.get("round")),
                TextNormalizer.cleanDisplay(m.get("role")),
                TextNormalizer.cleanDisplay(m.get("job_level")),
                TextNormalizer.cleanDisplay(m.get("source"))));
    }

    private static String metaKey(String raw) {
        String k = raw.toLowerCase(Locale.ROOT);
        return switch (k) {
            case "tag", "tags" -> "tags";
            case "company", "companies" -> "company";
            case "difficulty", "level" -> "difficulty";
            case "type", "question type" -> "type";
            case "round", "interview round" -> "round";
            case "job level" -> "job_level";
            default -> k;
        };
    }

    static boolean looksLikeQuestion(String s) {
        String c = clean(s);
        return !c.isEmpty() && c.length() <= 400 && (endsWithQuestionMark(c) || QUESTION_WORD.matcher(c).matches());
    }

    private static boolean endsWithQuestionMark(String s) {
        String c = s.replaceAll("[*_\\s]+$", "");
        return c.endsWith("?");
    }

    private static String headingText(String raw) {
        Matcher h = HEADING.matcher(raw == null ? "" : raw);
        return h.matches() ? h.group(2) : (raw == null ? "" : raw.trim());
    }

    /** Removes surrounding Markdown emphasis such as **text** or __text__. */
    static String clean(String s) {
        String c = s.trim();
        c = c.replaceAll("^[*_]{1,3}(?=\\S)", "").replaceAll("(?<=\\S)[*_]{1,3}$", "");
        return c.trim();
    }

    private static String stripTrailing(String s) { return s == null ? "" : s.replaceAll("\\s+$", ""); }

    private static String stripCommonIndent(List<String> lines) {
        int min = Integer.MAX_VALUE;
        for (String l : lines) {
            if (l.isBlank()) continue;
            int n = 0;
            while (n < l.length() && l.charAt(n) == ' ') n++;
            min = Math.min(min, n);
        }
        int cut = min == Integer.MAX_VALUE ? 0 : min;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (i > 0) sb.append('\n');
            sb.append(l.length() >= cut ? l.substring(cut) : l.trim());
        }
        return sb.toString();
    }
}
