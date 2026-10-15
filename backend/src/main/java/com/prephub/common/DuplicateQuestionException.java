package com.prephub.common;

/** Thrown when an exact duplicate (same fingerprint) exists. Carries the canonical match. */
public class DuplicateQuestionException extends RuntimeException {
    private final Long matchingQuestionId;
    private final String matchingText;
    public DuplicateQuestionException(Long id, String text) {
        super("An identical question already exists (id " + id + ")");
        this.matchingQuestionId = id; this.matchingText = text;
    }
    public Long getMatchingQuestionId() { return matchingQuestionId; }
    public String getMatchingText() { return matchingText; }
}
