package com.prephub.semantic;

import com.prephub.common.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Keeps question_embeddings and the in-memory {@link EmbeddingIndex} in sync with the questions table.
 * A question needs (re)embedding when it has no row, its text fingerprint changed, or the embedding model changed.
 * Runs on a schedule (default every 30 s) and on demand; never blocks user requests.
 */
@Component
public class EmbeddingIndexer {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingIndexer.class);
    static final int BATCH = 64, MAX_BATCHES_PER_RUN = 40;

    private final EmbeddingClient client;
    private final EmbeddingIndex index;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private volatile boolean loaded;
    private volatile String lastError;
    private volatile Instant lastRun;

    public EmbeddingIndexer(EmbeddingClient client, EmbeddingIndex index, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.client = client; this.index = index; this.jdbc = jdbc; this.tx = tx;
    }

    public record Status(boolean enabled, String model, int totalQuestions, int indexed, int pending, String lastError, Instant lastRun, String message) {}

    @Scheduled(initialDelayString = "${prephub.dedup.index-initial-delay-ms:5000}", fixedDelayString = "${prephub.dedup.index-interval-ms:30000}")
    public void scheduled() {
        try { syncNow(); } catch (RuntimeException e) { log.warn("Embedding sync failed: {}", e.getClass().getSimpleName()); }
    }

    @Async
    public void syncAsync() { scheduled(); }

    /** Synchronously embeds all pending questions (bounded per run). Returns the resulting status. */
    public synchronized Status syncNow() {
        if (!client.available()) return status();
        lastRun = Instant.now();
        loadFromDatabase();
        index.retainOnly(new HashSet<>(jdbc.queryForList("SELECT id FROM questions", Long.class)));
        for (int b = 0; b < MAX_BATCHES_PER_RUN; b++) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT q.id, q.question_text, q.fingerprint FROM questions q LEFT JOIN question_embeddings e ON e.question_id = q.id " +
                    "WHERE e.question_id IS NULL OR e.vector_data IS NULL OR e.model <> ? OR e.embedded_text_fingerprint <> q.fingerprint " +
                    "ORDER BY q.id LIMIT " + BATCH, client.model());
            if (rows.isEmpty()) break;
            List<String> texts = rows.stream().map(r -> embeddingText((String) r.get("question_text"))).toList();
            List<float[]> vectors;
            try {
                vectors = client.embed(texts);
                lastError = null;
            } catch (RuntimeException e) {
                lastError = e.getMessage();
                break;
            }
            for (int i = 0; i < rows.size(); i++) {
                Long id = ((Number) rows.get(i).get("id")).longValue();
                String fp = (String) rows.get(i).get("fingerprint");
                float[] v = VectorMath.normalize(vectors.get(i));
                store(id, fp, v);
                index.put(id, v);
            }
        }
        return status();
    }

    /** Text sent to the embedding model: the cleaned question text (answers would blur the comparison). */
    static String embeddingText(String questionText) {
        String c = TextNormalizer.cleanDisplay(questionText);
        return c == null ? "" : (c.length() > 4000 ? c.substring(0, 4000) : c);
    }

    private void store(Long id, String fingerprint, float[] v) {
        tx.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM question_embeddings WHERE question_id = ?", id);
            jdbc.update("INSERT INTO question_embeddings (question_id, model, dimensions, vector_ref, embedded_text_fingerprint, created_at, vector_data) " +
                    "VALUES (?, ?, ?, NULL, ?, ?, ?)", id, client.model(), v.length, fingerprint, Timestamp.from(Instant.now()), VectorMath.toBytes(v));
        });
    }

    private void loadFromDatabase() {
        if (loaded) return;
        jdbc.query("SELECT question_id, vector_data FROM question_embeddings WHERE model = ? AND vector_data IS NOT NULL", rs -> {
            byte[] b = rs.getBytes(2);
            try { index.put(rs.getLong(1), VectorMath.fromBytes(b)); } catch (IllegalArgumentException ignored) { /* re-embedded later */ }
        }, client.model());
        loaded = true;
    }

    /** Drops all stored vectors so everything is re-embedded (e.g. after changing the embedding model). */
    public synchronized void reset() {
        jdbc.update("DELETE FROM question_embeddings");
        index.clear();
        loaded = false;
    }

    public Status status() {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM questions", Integer.class);
        int t = total == null ? 0 : total;
        if (!client.available()) return new Status(false, null, t, 0, t, null, lastRun, client.status());
        int idx = loaded ? index.size() : countStored();
        int pending = Math.max(0, t - idx);
        String msg = pending == 0 ? "All questions indexed" : pending + " question(s) waiting to be embedded";
        return new Status(true, client.model(), t, idx, pending, lastError, lastRun, lastError != null ? "Last sync failed: " + lastError : msg);
    }

    private int countStored() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM question_embeddings WHERE model = ? AND vector_data IS NOT NULL", Integer.class, client.model());
        return n == null ? 0 : n;
    }

    public boolean ready() { return loaded; }
}
