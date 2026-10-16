package com.prephub.importer.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Text-based PDF parser (Apache PDFBox). Text is extracted page by page; references are "page P line L".
 * Scanned/image-only PDFs have no text layer and are rejected with a clear message (OCR is a future enhancement).
 */
public class PdfQuestionParser implements QuestionFileParser {
    @Override public String format() { return "pdf"; }
    @Override public Set<String> extensions() { return Set.of("pdf"); }

    @Override
    public ParsedFile parse(byte[] content, Map<String, String> ignored) {
        List<TextQuestionExtractor.SourceLine> lines = new ArrayList<>();
        int pages;
        try (PDDocument doc = Loader.loadPDF(content)) {
            pages = doc.getNumberOfPages();
            if (pages > ImportLimits.MAX_PDF_PAGES) throw new IllegalArgumentException("PDF has more than " + ImportLimits.MAX_PDF_PAGES + " pages");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int p = 1; p <= pages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String[] pageLines = stripper.getText(doc).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
                for (int l = 0; l < pageLines.length; l++)
                    lines.add(new TextQuestionExtractor.SourceLine("page " + p + " line " + (l + 1), pageLines[l]));
                lines.add(new TextQuestionExtractor.SourceLine("page " + p, ""));
            }
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("PDF is password-protected; remove the password and try again");
        } catch (IOException e) {
            throw new IllegalArgumentException("File is not a readable PDF");
        }
        long textChars = lines.stream().mapToLong(l -> l.text().replaceAll("\\s", "").length()).sum();
        if (textChars < 20)
            throw new IllegalArgumentException("No extractable text: this looks like a scanned PDF. OCR is not supported yet; export the document as text or DOCX");
        var r = TextQuestionExtractor.extract(lines);
        if (r.candidates().isEmpty()) throw new IllegalArgumentException("No questions found in the PDF. Mark questions with 'Q:' or end them with '?'");
        List<String> notes = new ArrayList<>(r.notes());
        notes.add(0, "Extracted text from " + pages + " page(s); line wrapping in PDFs can split questions, please review the preview");
        return new ParsedFile(List.of(), Map.of(), r.candidates(), notes);
    }
}
