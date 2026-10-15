package com.prephub.importer;

import java.util.List;

/**
 * Layer 2 duplicate detection (embeddings). Milestone 10 provides an embedding-backed implementation;
 * until then the no-op bean returns no matches so nothing is ever merged on a guess.
 */
public interface SemanticDuplicateDetector {
    record SemanticMatch(Long questionId, double score) {
    }

    List<SemanticMatch> findSimilar(String questionText, int limit);

    @org.springframework.stereotype.Component
    class NoOp implements SemanticDuplicateDetector {
        @Override
        public List<SemanticMatch> findSimilar(String questionText, int limit) {
            return List.of();
        }
    }
}
