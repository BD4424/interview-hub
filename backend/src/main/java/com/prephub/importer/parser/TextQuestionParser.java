package com.prephub.importer.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plain-text and Markdown parser. References are 1-based line numbers. */
public class TextQuestionParser implements QuestionFileParser {
    private final String format;
    private final Set<String> extensions;

    private TextQuestionParser(String format, Set<String> extensions) { this.format = format; this.extensions = extensions; }

    public static TextQuestionParser txt() { return new TextQuestionParser("txt", Set.of("txt")); }
    public static TextQuestionParser markdown() { return new TextQuestionParser("md", Set.of("md", "markdown")); }

    @Override public String format() { return format; }
    @Override public Set<String> extensions() { return extensions; }

    @Override
    public ParsedFile parse(byte[] content, Map<String, String> ignored) {
        String text = TextDecoder.decode(content).replace("\r\n", "\n").replace('\r', '\n');
        String[] raw = text.split("\n", -1);
        List<TextQuestionExtractor.SourceLine> lines = new ArrayList<>(raw.length);
        for (int i = 0; i < raw.length; i++) lines.add(new TextQuestionExtractor.SourceLine("line " + (i + 1), raw[i]));
        var r = TextQuestionExtractor.extract(lines);
        if (r.candidates().isEmpty()) throw new IllegalArgumentException("No questions found. Mark questions with 'Q:' (and answers with 'A:'), or end them with '?'");
        return new ParsedFile(List.of(), Map.of(), r.candidates(), r.notes());
    }
}
