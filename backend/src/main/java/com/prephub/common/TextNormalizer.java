package com.prephub.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/** Pure, deterministic normalization used for duplicate detection and name matching. */
public final class TextNormalizer {
    private TextNormalizer() {}

    /** Generic name normalization: NFKC, trim, collapse internal whitespace, lowercase (Locale.ROOT). */
    public static String normalizeName(String raw) {
        if (raw == null) return null;
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        return s.isEmpty() ? null : s;
    }

    /** Display-clean a name while keeping user casing: NFKC, trim, collapse whitespace. */
    public static String cleanDisplay(String raw) {
        if (raw == null) return null;
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC).replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? null : s;
    }

    /** Trims and normalizes line endings but keeps line breaks (questions may contain code). */
    public static String cleanDisplayKeepLines(String raw) {
        if (raw == null) return null;
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC).replace("\r\n", "\n").replace('\u00A0', ' ').trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * Canonical question text: name-normalization plus removal of trailing terminal punctuation
     * ("What is a Bean?" == "what is a bean"). Inner punctuation is preserved because it can change meaning
     * (e.g. code snippets).
     */
    public static String canonicalQuestion(String raw) {
        String s = normalizeName(raw);
        if (s == null) return null;
        s = s.replaceAll("[\\s?.!:;]+$", "").trim();
        return s.isEmpty() ? null : s;
    }

    public static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Fingerprint of a question = SHA-256 of canonical text; null if the question is blank. */
    public static String fingerprint(String questionText) {
        String c = canonicalQuestion(questionText);
        return c == null ? null : sha256(c);
    }

    public static String slugify(String raw) {
        String n = normalizeName(raw);
        if (n == null) return null;
        String s = Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? null : s;
    }
}
