package com.prephub.importer.parser;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Determines the import format from the extension and checks it against the file's magic bytes. */
public final class FormatDetector {
    private FormatDetector() {}

    public static final Set<String> TEXT_FORMATS = Set.of("csv", "txt", "md");

    public static String extension(String filename) {
        if (filename == null) return "";
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * @param format canonical format resolved from the extension (csv, xlsx, docx, pdf, txt, md)
     * @throws IllegalArgumentException when the content does not match the claimed format
     */
    public static void verify(String format, byte[] b) {
        switch (format) {
            case "xlsx", "docx" -> {
                if (!(b.length > 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4)) {
                    if (b.length > 8 && (b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF)
                        throw new IllegalArgumentException("This is a legacy Office file (.xls/.doc). Save it as ." + format + " and upload again");
                    throw new IllegalArgumentException("File content is not a ." + format + " document");
                }
            }
            case "pdf" -> {
                String head = new String(b, 0, Math.min(b.length, 1024), StandardCharsets.ISO_8859_1);
                if (!head.contains("%PDF-")) throw new IllegalArgumentException("File content is not a PDF");
            }
            default -> {
                int n = Math.min(b.length, 8192);
                for (int i = 0; i < n; i++)
                    if (b[i] == 0) throw new IllegalArgumentException("File looks binary, not a ." + format + " text file");
            }
        }
    }
}
