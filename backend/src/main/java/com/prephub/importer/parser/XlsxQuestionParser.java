package com.prephub.importer.parser;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.*;

/**
 * Dependency-free XLSX reader (OOXML SpreadsheetML via ZIP + StAX). Reads the first worksheet; row 1 is the header.
 * Supports shared strings (incl. rich-text runs), inline strings, formula string results, numbers and booleans.
 */
public class XlsxQuestionParser implements QuestionFileParser {
    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    @Override public String format() { return "xlsx"; }
    @Override public Set<String> extensions() { return Set.of("xlsx"); }
    @Override public boolean tabular() { return true; }

    public record Sheet(String name, List<TabularMapper.SourceRow> rows) {}

    @Override
    public ParsedFile parse(byte[] content, Map<String, String> mapping) {
        Sheet sheet = readFirstSheet(content);
        if (sheet.rows().isEmpty()) throw new IllegalArgumentException("The first worksheet is empty");
        List<String> header = sheet.rows().get(0).cells();
        List<TabularMapper.SourceRow> data = sheet.rows().subList(1, sheet.rows().size());
        ParsedFile pf = TabularMapper.map(header, data, mapping);
        List<String> notes = new ArrayList<>(pf.notes());
        notes.add(0, "Read worksheet '" + sheet.name() + "' (only the first worksheet is imported)");
        return new ParsedFile(pf.headers(), pf.appliedMapping(), pf.candidates(), notes);
    }

    public static Sheet readFirstSheet(byte[] content) {
        Map<String, byte[]> parts = SafeXml.unzip(content, Set.of("xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/sharedStrings.xml", "xl/worksheets/"));
        byte[] wb = parts.get("xl/workbook.xml");
        if (wb == null) throw new IllegalArgumentException("Not an Excel workbook (xl/workbook.xml missing). Legacy .xls files must be saved as .xlsx");
        String[] first = firstSheet(wb);
        String target = resolveTarget(parts.get("xl/_rels/workbook.xml.rels"), first[1]);
        byte[] sheetXml = target == null ? null : parts.get(target);
        if (sheetXml == null) sheetXml = parts.get("xl/worksheets/sheet1.xml");
        if (sheetXml == null) throw new IllegalArgumentException("Workbook has no readable worksheet");
        List<String> shared = sharedStrings(parts.get("xl/sharedStrings.xml"));
        String sheetRef = first[0] == null ? "Sheet1" : first[0];
        return new Sheet(sheetRef, rows(sheetXml, shared, sheetRef));
    }

    /** @return {sheetName, relationshipId} of the first sheet in workbook order */
    private static String[] firstSheet(byte[] wb) {
        XMLStreamReader r = SafeXml.reader(wb);
        try {
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && "sheet".equals(r.getLocalName()))
                    return new String[]{r.getAttributeValue(null, "name"), r.getAttributeValue(REL_NS, "id")};
            }
        } catch (XMLStreamException e) { throw new IllegalArgumentException("Malformed workbook.xml"); }
        throw new IllegalArgumentException("Workbook contains no worksheets");
    }

    private static String resolveTarget(byte[] rels, String relId) {
        if (rels == null || relId == null) return null;
        XMLStreamReader r = SafeXml.reader(rels);
        try {
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && "Relationship".equals(r.getLocalName())
                        && relId.equals(r.getAttributeValue(null, "Id"))) {
                    String t = r.getAttributeValue(null, "Target");
                    if (t == null) return null;
                    return t.startsWith("/") ? t.substring(1) : "xl/" + t;
                }
            }
        } catch (XMLStreamException e) { return null; }
        return null;
    }

    private static List<String> sharedStrings(byte[] xml) {
        List<String> out = new ArrayList<>();
        if (xml == null) return out;
        XMLStreamReader r = SafeXml.reader(xml);
        StringBuilder cur = null;
        int phonetic = 0;
        try {
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "si" -> cur = new StringBuilder();
                        case "rPh" -> phonetic++;
                        case "t" -> { if (cur != null && phonetic == 0) cur.append(r.getElementText()); }
                        default -> { }
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    if ("si".equals(r.getLocalName()) && cur != null) { out.add(cur.toString()); cur = null; }
                    else if ("rPh".equals(r.getLocalName())) phonetic--;
                }
            }
        } catch (XMLStreamException e) { throw new IllegalArgumentException("Malformed sharedStrings.xml"); }
        return out;
    }

    private static List<TabularMapper.SourceRow> rows(byte[] xml, List<String> shared, String sheetName) {
        List<TabularMapper.SourceRow> rows = new ArrayList<>();
        XMLStreamReader r = SafeXml.reader(xml);
        List<String> cells = null;
        int rowNum = 0, nextCol = 0;
        String cellType = null, cellValue = null;
        int cellCol = 0;
        try {
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "row" -> {
                            String rn = r.getAttributeValue(null, "r");
                            rowNum = rn != null ? Integer.parseInt(rn) : rowNum + 1;
                            cells = new ArrayList<>();
                            nextCol = 0;
                        }
                        case "c" -> {
                            String ref = r.getAttributeValue(null, "r");
                            cellCol = ref != null ? columnIndex(ref) : nextCol;
                            cellType = r.getAttributeValue(null, "t");
                            cellValue = null;
                        }
                        case "v" -> cellValue = r.getElementText();
                        case "t" -> { // inline string <is><t>
                            String t = r.getElementText();
                            cellValue = cellValue == null ? t : cellValue + t;
                        }
                        default -> { }
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    if ("c".equals(r.getLocalName()) && cells != null) {
                        while (cells.size() < cellCol) cells.add("");
                        cells.add(cellText(cellType, cellValue, shared));
                        nextCol = cellCol + 1;
                    } else if ("row".equals(r.getLocalName()) && cells != null) {
                        rows.add(new TabularMapper.SourceRow(sheetName + " row " + rowNum, cells));
                        cells = null;
                    }
                }
            }
        } catch (XMLStreamException | NumberFormatException e) {
            throw new IllegalArgumentException("Malformed worksheet XML");
        }
        return rows;
    }

    private static String cellText(String type, String v, List<String> shared) {
        if (v == null) return "";
        if ("s".equals(type)) {
            int i = Integer.parseInt(v.trim());
            return i >= 0 && i < shared.size() ? shared.get(i) : "";
        }
        if ("b".equals(type)) return "1".equals(v.trim()) ? "TRUE" : "FALSE";
        if ("e".equals(type)) return "";
        if (type == null || "n".equals(type)) {
            String t = v.trim();
            if (t.endsWith(".0")) t = t.substring(0, t.length() - 2);
            return t;
        }
        return v; // str, inlineStr
    }

    /** "AB12" -> 27 (zero-based column index). */
    static int columnIndex(String ref) {
        int col = 0, i = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i))) {
            col = col * 26 + (Character.toUpperCase(ref.charAt(i)) - 'A' + 1);
            i++;
        }
        if (col == 0) throw new NumberFormatException("Bad cell reference " + ref);
        return col - 1;
    }
}
