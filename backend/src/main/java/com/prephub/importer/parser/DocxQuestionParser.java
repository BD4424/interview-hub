package com.prephub.importer.parser;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.*;

/**
 * Dependency-free DOCX parser (WordprocessingML via ZIP + StAX). Paragraph text is extracted in document order
 * (including table cells); Heading 1–6 styles become Markdown headings so the shared {@link TextQuestionExtractor}
 * treats them as section names or questions. References are "paragraph N".
 */
public class DocxQuestionParser implements QuestionFileParser {
    @Override public String format() { return "docx"; }
    @Override public Set<String> extensions() { return Set.of("docx"); }

    @Override
    public ParsedFile parse(byte[] content, Map<String, String> ignored) {
        List<TextQuestionExtractor.SourceLine> lines = paragraphs(content);
        var r = TextQuestionExtractor.extract(lines);
        if (r.candidates().isEmpty()) throw new IllegalArgumentException("No questions found in the document. Mark questions with 'Q:' or end them with '?'");
        return new ParsedFile(List.of(), Map.of(), r.candidates(), r.notes());
    }

    public static List<TextQuestionExtractor.SourceLine> paragraphs(byte[] content) {
        Map<String, byte[]> parts = SafeXml.unzip(content, Set.of("word/document.xml"));
        byte[] doc = parts.get("word/document.xml");
        if (doc == null) throw new IllegalArgumentException("Not a Word document (word/document.xml missing). Legacy .doc files must be saved as .docx");
        List<TextQuestionExtractor.SourceLine> out = new ArrayList<>();
        XMLStreamReader r = SafeXml.reader(doc);
        StringBuilder p = null;
        int heading = 0, para = 0;
        boolean numbered = false;
        try {
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "p" -> { p = new StringBuilder(); heading = 0; numbered = false; }
                        case "pStyle" -> {
                            String v = r.getAttributeValue(r.getNamespaceURI(), "val");
                            if (v == null) v = r.getAttributeValue(null, "val");
                            heading = headingLevel(v);
                        }
                        case "numPr" -> numbered = true;
                        case "t" -> { if (p != null) p.append(r.getElementText()); }
                        case "tab" -> { if (p != null) p.append('\t'); }
                        case "br", "cr" -> { if (p != null) p.append('\n'); }
                        default -> { }
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT && "p".equals(r.getLocalName()) && p != null) {
                    para++;
                    String text = p.toString();
                    String[] split = text.split("\n", -1);
                    for (int k = 0; k < split.length; k++) {
                        String line = split[k];
                        if (k == 0 && heading > 0 && !line.isBlank()) line = "#".repeat(heading) + " " + line;
                        else if (k == 0 && numbered && !line.isBlank()) line = "- " + line;
                        out.add(new TextQuestionExtractor.SourceLine("paragraph " + para, line));
                    }
                    out.add(new TextQuestionExtractor.SourceLine("paragraph " + para, "")); // paragraph boundary
                    p = null;
                }
            }
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("Malformed Word document XML");
        }
        return out;
    }

    /** "Heading1", "heading 2", "Title" -> level; anything else 0. */
    static int headingLevel(String style) {
        if (style == null) return 0;
        String s = style.replace(" ", "").toLowerCase(Locale.ROOT);
        if (s.equals("title")) return 1;
        if (s.startsWith("heading") && s.length() > 7) {
            try { int n = Integer.parseInt(s.substring(7)); return n >= 1 && n <= 6 ? n : 0; }
            catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
