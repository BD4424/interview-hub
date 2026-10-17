package com.prephub.practice;

public final class PracticeEnums {
    private PracticeEnums() {}
    public enum SessionType { TOPIC, COMPANY, TAG, RANDOM, MOCK, REVISION, SINGLE }
    public enum SessionStatus { ACTIVE, COMPLETED }
    /** BANK: a question from the bank. FOLLOW_UP: AI follow-up that exists only in this session (no question row, no company). */
    public enum TurnType { BANK, FOLLOW_UP }
    public enum TurnStatus { ASKED, AWAITING_SELF_SCORE, EVALUATED, SKIPPED }
    public enum Confidence { LOW, MEDIUM, HIGH }
}
