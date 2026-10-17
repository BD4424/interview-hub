package com.prephub.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Layer 2 duplicate detection (embeddings). Implemented by {@code com.prephub.semantic.EmbeddingDuplicateDetector}.
 * Only matches at or above the "possible" threshold are returned; callers classify with {@link #likelyThreshold()}.
 */
public interface SemanticDuplicateDetector {
    record SemanticMatch(Long questionId, double score) {}

    /** @param matches one list per input text (same order); empty lists when nothing similar or when unchecked
     *  @param note    non-null when the check could not run (disabled, provider error) */
    record BatchResult(List<List<SemanticMatch>> matches, String note) {}

    List<SemanticMatch> findSimilar(String questionText, int limit);

    default BatchResult findSimilarBatch(List<String> texts, int limit) {
        List<List<SemanticMatch>> out = new ArrayList<>();
        for (String t : texts) out.add(findSimilar(t, limit));
        return new BatchResult(out, null);
    }

    boolean enabled();
    double likelyThreshold();
    double possibleThreshold();
}
