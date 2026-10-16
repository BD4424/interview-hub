package com.prephub.importer.parser;

import java.util.Map;
import java.util.Set;

/** A format-specific parser that turns raw file bytes into format-independent question candidates. */
public interface QuestionFileParser {
    /** Lower-case canonical format id, e.g. "csv". */
    String format();

    /** File extensions (lower case, without dot) handled by this parser. */
    Set<String> extensions();

    /** True if the format has columns that can be mapped by the user. */
    default boolean tabular() { return false; }

    /**
     * @param columnMapping optional header -> field override for tabular formats; ignored by text formats.
     * @throws IllegalArgumentException with a user-facing message when the file cannot be parsed.
     */
    ParsedFile parse(byte[] content, Map<String, String> columnMapping);
}
