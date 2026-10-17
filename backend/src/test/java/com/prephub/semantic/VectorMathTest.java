package com.prephub.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class VectorMathTest {
    @Test void normaliseDotAndRoundTrip() {
        float[] a = VectorMath.normalize(new float[]{3, 4});
        assertThat(a[0]).isCloseTo(0.6f, within(1e-6f));
        assertThat(VectorMath.dot(a, a)).isCloseTo(1.0, within(1e-6));
        assertThat(VectorMath.cosine(new float[]{1, 0}, new float[]{0, 1})).isCloseTo(0.0, within(1e-9));
        assertThat(VectorMath.fromBytes(VectorMath.toBytes(a))).containsExactly(a);
        assertThatThrownBy(() -> VectorMath.normalize(new float[]{0, 0})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VectorMath.fromBytes(new byte[]{1, 2, 3})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void indexReturnsTopMatchesAboveThreshold() {
        EmbeddingIndex idx = new EmbeddingIndex();
        idx.put(1L, VectorMath.normalize(new float[]{1, 0, 0}));
        idx.put(2L, VectorMath.normalize(new float[]{0.9f, 0.1f, 0}));
        idx.put(3L, VectorMath.normalize(new float[]{0, 1, 0}));
        idx.put(4L, VectorMath.normalize(new float[]{1, 0}));            // other model's dimensions: ignored
        var hits = idx.nearest(VectorMath.normalize(new float[]{1, 0, 0}), 5, 0.5, Set.of());
        assertThat(hits).extracting(EmbeddingIndex.Hit::questionId).containsExactly(1L, 2L);
        assertThat(idx.nearest(VectorMath.normalize(new float[]{1, 0, 0}), 1, 0.5, Set.of(1L)))
                .extracting(EmbeddingIndex.Hit::questionId).isEqualTo(List.of(2L));
    }
}
