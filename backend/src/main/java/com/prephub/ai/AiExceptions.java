package com.prephub.ai;

import java.util.List;

public final class AiExceptions {
    private AiExceptions() {}

    /** AI is disabled or not configured (503). */
    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String msg) { super(msg); }
    }

    /** The model replied, but not with usable output (502). */
    public static class AiOutputException extends RuntimeException {
        private final List<String> problems;
        public AiOutputException(String msg, List<String> problems) { super(msg); this.problems = problems; }
        public List<String> getProblems() { return problems; }
    }

    /** The provider call itself failed: network, auth, quota (502). */
    public static class AiProviderException extends RuntimeException {
        public AiProviderException(String msg) { super(msg); }
    }
}
