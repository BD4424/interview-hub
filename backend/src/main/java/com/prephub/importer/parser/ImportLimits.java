package com.prephub.importer.parser;

/** Hard limits that protect the server from oversized or malicious files (zip bombs, huge PDFs). */
public final class ImportLimits {
    private ImportLimits() {}
    public static final int MAX_CANDIDATES = 5000;
    public static final int MAX_ZIP_ENTRIES = 5000;
    public static final long MAX_ZIP_ENTRY_BYTES = 50L * 1024 * 1024;
    public static final long MAX_ZIP_TOTAL_BYTES = 150L * 1024 * 1024;
    public static final int MAX_PDF_PAGES = 500;
}
