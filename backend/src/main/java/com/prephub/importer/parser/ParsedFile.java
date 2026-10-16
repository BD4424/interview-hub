package com.prephub.importer.parser;

import com.prephub.importer.QuestionCandidate;

import java.util.List;
import java.util.Map;

/**
 * Common output of every format parser.
 *
 * @param headers         source column headers (tabular formats only; empty for text formats)
 * @param appliedMapping  header -> application field actually used (tabular formats only)
 * @param candidates      extracted question candidates
 * @param notes           human-readable extraction notes (ignored lines, rules applied, ...)
 */
public record ParsedFile(List<String> headers, Map<String, String> appliedMapping,
                         List<QuestionCandidate> candidates, List<String> notes) {}
