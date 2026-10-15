package com.prephub.importer;

import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 CSV reader: quoted fields, escaped quotes, embedded newlines, CRLF, UTF-8 BOM. */
public final class CsvReader {
    private CsvReader() {}

    public static List<List<String>> parse(String content) {
        if (content.startsWith("\uFEFF")) content = content.substring(1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else inQuotes = false;
                } else field.append(c);
            } else if (c == '"' && field.length() == 0) {
                inQuotes = true; fieldStarted = true;
            } else if (c == ',') {
                row.add(field.toString()); field.setLength(0); fieldStarted = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty() && !fieldStarted)) rows.add(row);
                row = new ArrayList<>(); fieldStarted = false;
            } else { field.append(c); fieldStarted = true; }
        }
        if (inQuotes) throw new IllegalArgumentException("Unterminated quoted field in CSV");
        if (field.length() > 0 || fieldStarted || !row.isEmpty()) { row.add(field.toString()); rows.add(row); }
        return rows;
    }
}
