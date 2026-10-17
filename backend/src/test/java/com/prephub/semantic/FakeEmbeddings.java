package com.prephub.semantic;

import java.util.*;

/** Deterministic bag-of-words "embeddings" for tests: identical word sets → 1.0, partial overlap → cosine of overlap. */
public final class FakeEmbeddings {
    private FakeEmbeddings() {}
    static final Set<String> STOP = Set.of("the", "a", "an", "of", "in", "is", "how", "does", "do", "what", "to", "and");

    public static float[] embed(String text) {
        float[] v = new float[1024];
        for (String w : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").split("\\s+")) {
            if (w.isBlank() || STOP.contains(w)) continue;
            v[Math.floorMod(w.hashCode(), v.length)] += 1;
        }
        v[1023] += 0.001f; // never a zero vector
        return v;
    }

    public static List<float[]> embedAll(List<String> texts) { return texts.stream().map(FakeEmbeddings::embed).toList(); }
}
