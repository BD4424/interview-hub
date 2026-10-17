package com.prephub.semantic;

import com.prephub.common.TextNormalizer;
import com.prephub.importer.SemanticDuplicateDetector;
import com.prephub.question.Question;
import com.prephub.question.QuestionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/dedup")
public class DedupController {
    static final int CALIBRATION_CAP = 1500;

    private final SemanticDuplicateDetector detector;
    private final EmbeddingIndexer indexer;
    private final EmbeddingIndex index;
    private final QuestionRepository questions;

    public DedupController(SemanticDuplicateDetector detector, EmbeddingIndexer indexer, EmbeddingIndex index, QuestionRepository questions) {
        this.detector = detector; this.indexer = indexer; this.index = index; this.questions = questions;
    }

    public record CheckRequest(@NotBlank @Size(max = 10000) String text, Long excludeQuestionId) {}
    public record Match(Long questionId, String questionText, String topic, String approvalStatus, double score, String classification) {}
    public record CheckResult(Match exact, List<Match> similar, boolean semanticEnabled, double likelyThreshold, double possibleThreshold, String note) {}

    /** Used by the question form (before saving) and the Settings similarity checker. Read-only. */
    @PostMapping("/check") @Transactional(readOnly = true)
    public CheckResult check(@Valid @RequestBody CheckRequest r) {
        String fp = TextNormalizer.fingerprint(r.text());
        Match exact = fp == null ? null : questions.findByFingerprint(fp)
                .filter(q -> !q.getId().equals(r.excludeQuestionId()))
                .map(q -> match(q, 1.0, "EXACT_DUPLICATE")).orElse(null);
        var batch = detector.findSimilarBatch(List.of(r.text()), 6);
        List<Match> similar = new ArrayList<>();
        Map<Long, Question> byId = load(batch.matches().get(0).stream().map(SemanticDuplicateDetector.SemanticMatch::questionId).toList());
        for (var m : batch.matches().get(0)) {
            if (m.questionId().equals(r.excludeQuestionId()) || (exact != null && m.questionId().equals(exact.questionId()))) continue;
            Question q = byId.get(m.questionId());
            if (q != null) similar.add(match(q, m.score(), classify(m.score())));
        }
        return new CheckResult(exact, similar, detector.enabled(), detector.likelyThreshold(), detector.possibleThreshold(), batch.note());
    }

    @GetMapping("/status")
    public EmbeddingIndexer.Status status() { return indexer.status(); }

    /** Embeds pending questions now. {@code reset=true} re-embeds everything (use after changing the embedding model). */
    @PostMapping("/reindex")
    public EmbeddingIndexer.Status reindex(@RequestParam(defaultValue = "false") boolean reset) {
        if (reset) indexer.reset();
        return indexer.syncNow();
    }

    public record Pair(Match a, Match b, double score) {}
    public record HistogramBucket(String range, int questions) {}
    public record Calibration(double likelyThreshold, double possibleThreshold, int sampled, List<Pair> closestPairs,
                              List<HistogramBucket> nearestNeighbourScores, String guidance) {}

    /**
     * Shows how scores behave on YOUR bank: the closest pairs and the distribution of each question's nearest-neighbour
     * score. Pick "likely" just below the scores of pairs you consider true duplicates and "possible" around the point
     * where pairs stop being near-duplicates. Set them via DEDUP_LIKELY_THRESHOLD / DEDUP_POSSIBLE_THRESHOLD.
     */
    @GetMapping("/calibration") @Transactional(readOnly = true)
    public Calibration calibration(@RequestParam(defaultValue = "25") int pairs) {
        if (!indexer.ready()) indexer.syncNow();
        List<Long> ids = index.ids().stream().sorted(Comparator.reverseOrder()).limit(CALIBRATION_CAP).toList();
        record P(Long a, Long b, double s) {}
        PriorityQueue<P> top = new PriorityQueue<>(Comparator.comparingDouble(P::s));
        double[] best = new double[ids.size()];
        Arrays.fill(best, -1);
        for (int i = 0; i < ids.size(); i++) {
            float[] va = index.get(ids.get(i));
            if (va == null) continue;
            for (int j = i + 1; j < ids.size(); j++) {
                float[] vb = index.get(ids.get(j));
                if (vb == null || vb.length != va.length) continue;
                double s = VectorMath.dot(va, vb);
                best[i] = Math.max(best[i], s);
                best[j] = Math.max(best[j], s);
                top.add(new P(ids.get(i), ids.get(j), s));
                if (top.size() > Math.min(Math.max(pairs, 1), 100)) top.poll();
            }
        }
        List<P> sorted = new ArrayList<>(top);
        sorted.sort(Comparator.comparingDouble(P::s).reversed());
        Map<Long, Question> byId = load(sorted.stream().flatMap(p -> java.util.stream.Stream.of(p.a(), p.b())).distinct().toList());
        List<Pair> out = sorted.stream().filter(p -> byId.containsKey(p.a()) && byId.containsKey(p.b()))
                .map(p -> new Pair(match(byId.get(p.a()), p.s(), classify(p.s())), match(byId.get(p.b()), p.s(), classify(p.s())), VectorMath.round(p.s())))
                .toList();
        String[] labels = {"< 0.60", "0.60–0.70", "0.70–0.80", "0.80–0.85", "0.85–0.90", "0.90–0.95", "≥ 0.95"};
        double[] edges = {0.60, 0.70, 0.80, 0.85, 0.90, 0.95};
        int[] counts = new int[labels.length];
        for (double b : best) {
            if (b < 0) continue;
            int k = 0;
            while (k < edges.length && b >= edges[k]) k++;
            counts[k]++;
        }
        List<HistogramBucket> hist = new ArrayList<>();
        for (int k = 0; k < labels.length; k++) hist.add(new HistogramBucket(labels[k], counts[k]));
        return new Calibration(detector.likelyThreshold(), detector.possibleThreshold(), ids.size(), out, hist,
                "Review the closest pairs: true paraphrases should be at or above 'likely'; related-but-different questions "
                + "(e.g. HashMap collisions vs ConcurrentHashMap locking) should fall below 'possible'.");
    }

    private Map<Long, Question> load(Collection<Long> ids) {
        return questions.findAllById(ids).stream().collect(Collectors.toMap(Question::getId, Function.identity()));
    }

    private String classify(double s) {
        return s >= detector.likelyThreshold() ? "LIKELY_SEMANTIC_DUPLICATE" : s >= detector.possibleThreshold() ? "POSSIBLE_DUPLICATE" : "RELATED";
    }

    private static Match match(Question q, double score, String cls) {
        return new Match(q.getId(), q.getQuestionText(), q.getTopic().getName(), q.getApprovalStatus().name(), VectorMath.round(score), cls);
    }
}
