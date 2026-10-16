package com.prephub.importer.parser;

import com.prephub.importer.CsvQuestionParser;
import org.springframework.stereotype.Component;

import java.util.*;

/** Central lookup of format parsers by extension. */
@Component
public class ParserRegistry {
    private final Map<String, QuestionFileParser> byExtension = new LinkedHashMap<>();
    private final Map<String, QuestionFileParser> byFormat = new LinkedHashMap<>();

    public ParserRegistry() {
        for (QuestionFileParser p : List.of(new CsvQuestionParser(), new XlsxQuestionParser(), new DocxQuestionParser(),
                new PdfQuestionParser(), TextQuestionParser.txt(), TextQuestionParser.markdown())) {
            byFormat.put(p.format(), p);
            p.extensions().forEach(e -> byExtension.put(e, p));
        }
    }

    public Set<String> supportedExtensions() { return Collections.unmodifiableSet(byExtension.keySet()); }

    /** Resolves and verifies the format of an upload; returns the canonical format id. */
    public String detect(String filename, byte[] content) {
        String ext = FormatDetector.extension(filename);
        QuestionFileParser p = byExtension.get(ext);
        if (p == null) throw new IllegalArgumentException("Unsupported file type '." + ext + "'. Supported: " + String.join(", ", byExtension.keySet()));
        FormatDetector.verify(p.format(), content);
        return p.format();
    }

    public QuestionFileParser get(String format) {
        QuestionFileParser p = byFormat.get(format);
        if (p == null) throw new IllegalArgumentException("Unknown format " + format);
        return p;
    }
}
