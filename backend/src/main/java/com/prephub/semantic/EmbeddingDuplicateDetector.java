package com.prephub.semantic;

import com.prephub.importer.SemanticDuplicateDetector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Embedding-based semantic duplicate detection. Thresholds are configurable and should be calibrated with
 * {@code GET /api/v1/dedup/calibration} against your own bank: same-topic but different questions typically score
 * well below true paraphrases, but the exact gap depends on the embedding model.
 */
@Component
public class EmbeddingDuplicateDetector implements SemanticDuplicateDetector {
    private final EmbeddingClient client;
    private final EmbeddingIndex index;
    private final EmbeddingIndexer indexer;
    private final double likely, possible;

    public EmbeddingDuplicateDetector(EmbeddingClient client, EmbeddingIndex index, EmbeddingIndexer indexer,
                                      @Value("${prephub.dedup.likely-threshold:0.90}") double likely,
                                      @Value("${prephub.dedup.possible-threshold:0.82}") double possible) {
        if (possible > likely) throw new IllegalStateException("prephub.dedup.possible-threshold must be <= likely-threshold");
        this.client = client; this.index = index; this.indexer = indexer; this.likely = likely; this.possible = possible;
    }

    @Override public boolean enabled() { return client.available(); }
    @Override public double likelyThreshold() { return likely; }
    @Override public double possibleThreshold() { return possible; }

    @Override
    public List<SemanticMatch> findSimilar(String text, int limit) {
        BatchResult r = findSimilarBatch(List.of(text == null ? "" : text), limit);
        return r.matches().get(0);
    }

    @Override
    public BatchResult findSimilarBatch(List<String> texts, int limit) {
        List<List<SemanticMatch>> out = new ArrayList<>();
        texts.forEach(t -> out.add(List.of()));
        if (texts.isEmpty()) return new BatchResult(out, null);
        if (!client.available()) return new BatchResult(out, "Semantic duplicate check is off: " + client.status());
        if (!indexer.ready()) indexer.syncNow();
        try {
            for (int from = 0; from < texts.size(); from += EmbeddingIndexer.BATCH) {
                List<String> chunk = texts.subList(from, Math.min(texts.size(), from + EmbeddingIndexer.BATCH));
                List<Integer> positions = new ArrayList<>();
                List<String> payload = new ArrayList<>();
                for (int i = 0; i < chunk.size(); i++) {
                    String t = EmbeddingIndexer.embeddingText(chunk.get(i));
                    if (!t.isEmpty()) { positions.add(from + i); payload.add(t); }
                }
                if (payload.isEmpty()) continue;
                List<float[]> vectors = client.embed(payload);
                for (int k = 0; k < payload.size(); k++) {
                    float[] q = VectorMath.normalize(vectors.get(k));
                    out.set(positions.get(k), index.nearest(q, limit, possible, Set.of()).stream()
                            .map(h -> new SemanticMatch(h.questionId(), VectorMath.round(h.score()))).toList());
                }
            }
        } catch (RuntimeException e) {
            return new BatchResult(out, "Semantic duplicate check failed (" + e.getMessage() + "); only exact duplicates were checked");
        }
        var st = indexer.status();
        String note = st.pending() > 0 ? "Semantic index is still building (" + st.indexed() + "/" + st.totalQuestions() + " questions); some similar questions may not be flagged yet" : null;
        return new BatchResult(out, note);
    }
}
