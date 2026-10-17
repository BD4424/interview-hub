package com.prephub.semantic;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory index of normalised question vectors. Brute-force search is exact and fast enough for a personal bank
 * (5,000 questions x 1,536 dims is ~8M multiply-adds per query, a few milliseconds).
 * The database (question_embeddings.vector_data) is the source of truth; {@link EmbeddingIndexer} keeps this in sync.
 */
@Component
public class EmbeddingIndex {
    public record Hit(Long questionId, double score) {}

    private final Map<Long, float[]> vectors = new ConcurrentHashMap<>();

    public void put(Long questionId, float[] normalised) { vectors.put(questionId, normalised); }
    public void remove(Long questionId) { vectors.remove(questionId); }
    public void retainOnly(Set<Long> ids) { vectors.keySet().retainAll(ids); }
    public void clear() { vectors.clear(); }
    public int size() { return vectors.size(); }
    public Set<Long> ids() { return Set.copyOf(vectors.keySet()); }
    public float[] get(Long id) { return vectors.get(id); }

    public List<Hit> nearest(float[] normalisedQuery, int limit, double minScore, Set<Long> exclude) {
        PriorityQueue<Hit> top = new PriorityQueue<>(Comparator.comparingDouble(Hit::score));
        for (var e : vectors.entrySet()) {
            if (exclude != null && exclude.contains(e.getKey())) continue;
            float[] v = e.getValue();
            if (v.length != normalisedQuery.length) continue; // vector from another model; reindex pending
            double s = VectorMath.dot(normalisedQuery, v);
            if (s < minScore) continue;
            top.add(new Hit(e.getKey(), s));
            if (top.size() > limit) top.poll();
        }
        List<Hit> out = new ArrayList<>(top);
        out.sort(Comparator.comparingDouble(Hit::score).reversed());
        return out;
    }
}
