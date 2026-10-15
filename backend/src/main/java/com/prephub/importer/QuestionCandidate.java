package com.prephub.importer;

import java.util.List;

/** Format-independent output of every parser (CSV now; XLSX/DOCX/PDF/TXT/MD parsers produce the same model). */
public record QuestionCandidate(
        String sourceReference,
        String question,
        String answer,
        String topic,
        List<String> tags,
        List<String> companies,     // explicit row-level companies, empty = not supplied
        String difficulty,
        String questionType,
        String interviewRound,
        String role,
        String jobLevel,
        String sourceNote) {}
