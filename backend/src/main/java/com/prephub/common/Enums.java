package com.prephub.common;

/** Domain enums grouped in one file for discoverability. */
public final class Enums {
    private Enums() {}
    public enum Difficulty { BEGINNER, INTERMEDIATE, ADVANCED }
    public enum QuestionType { CONCEPTUAL, CODING, SCENARIO_BASED, DEBUGGING, SYSTEM_DESIGN }
    public enum AnswerStatus { NONE, UNVERIFIED, VERIFIED }
    public enum SourceType { IMPORTED, MANUAL, AI_GENERATED }
    public enum ApprovalStatus { PENDING, APPROVED, REJECTED }
    public enum AttributionStatus { REPORTED, VERIFIED, UNVERIFIED }
    public enum AttributionMode { NONE, SINGLE_COMPANY, PER_ROW }
    public enum ImportStatus { PROCESSING, READY_FOR_REVIEW, COMPLETED, FAILED }
    public enum MatchType { NEW_CANDIDATE, EXACT_DUPLICATE, DUPLICATE_IN_FILE, LIKELY_SEMANTIC_DUPLICATE, POSSIBLE_DUPLICATE, INVALID }
    public enum ReviewStatus { PENDING, NEEDS_REVIEW, AUTO_SKIPPED, IMPORTED, MERGED, REJECTED, INVALID }
    public enum ReviewAction { APPROVE, REJECT, MERGE, ADD_COMPANY, KEEP_SEPARATE }

    /** Lenient enum parsing for import data: "Scenario-Based" -> SCENARIO_BASED. Returns null on blank/unknown. */
    public static <E extends Enum<E>> E parseLenient(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[\\s-]+", "_");
        for (E e : type.getEnumConstants()) if (e.name().equals(key)) return e;
        return null;
    }
}
