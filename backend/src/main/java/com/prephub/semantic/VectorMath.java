package com.prephub.semantic;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Framework-free vector helpers. Vectors are L2-normalised once so similarity is a plain dot product. */
public final class VectorMath {
    private VectorMath() {}

    public static float[] normalize(float[] v) {
        double s = 0;
        for (float x : v) s += (double) x * x;
        double n = Math.sqrt(s);
        if (n == 0) throw new IllegalArgumentException("zero vector");
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / n);
        return out;
    }

    /** Dot product of two normalised vectors = cosine similarity in [-1, 1]. */
    public static double dot(float[] a, float[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("dimension mismatch " + a.length + " vs " + b.length);
        double s = 0;
        for (int i = 0; i < a.length; i++) s += (double) a[i] * b[i];
        return s;
    }

    public static double cosine(float[] a, float[] b) { return dot(normalize(a), normalize(b)); }

    public static byte[] toBytes(float[] v) {
        ByteBuffer bb = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float x : v) bb.putFloat(x);
        return bb.array();
    }

    public static float[] fromBytes(byte[] b) {
        if (b == null || b.length % 4 != 0) throw new IllegalArgumentException("corrupt vector");
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[b.length / 4];
        for (int i = 0; i < v.length; i++) v[i] = bb.getFloat();
        return v;
    }

    /** Rounds a similarity for display/storage (3 decimals). */
    public static double round(double s) { return Math.round(s * 1000.0) / 1000.0; }
}
