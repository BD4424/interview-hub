package com.prephub.semantic;

import java.util.List;

/** Provider-neutral embeddings. Built in {@code AiConfig}; replaced by a fake in tests. */
public interface EmbeddingClient {
    boolean available();
    String model();
    String status();
    /** One vector per input, same order. @throws RuntimeException on provider failure */
    List<float[]> embed(List<String> texts);

    final class Disabled implements EmbeddingClient {
        private final String reason;
        public Disabled(String reason) { this.reason = reason; }
        @Override public boolean available() { return false; }
        @Override public String model() { return null; }
        @Override public String status() { return reason; }
        @Override public List<float[]> embed(List<String> texts) { throw new IllegalStateException(reason); }
    }
}
