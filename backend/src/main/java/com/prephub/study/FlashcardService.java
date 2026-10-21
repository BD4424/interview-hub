package com.prephub.study;

import com.prephub.common.BadRequestException;
import com.prephub.common.NotFoundException;
import com.prephub.practice.PracticeMath;
import com.prephub.study.StudyQueries.QView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Flashcards: quick self-graded recall without AI cost. Grades are stored as attempts (mode FLASHCARD) so they feed
 * Smart revision, spaced repetition and progress: KNEW = 9, SHAKY = 5, DIDNT = 2.
 */
@Service
public class FlashcardService {
    static final int MAX_DECK = 100;

    public enum Scope { DUE, NEW, ALL }
    public enum Grade {
        KNEW(9.0, "HIGH"), SHAKY(5.0, "MEDIUM"), DIDNT(2.0, "LOW");
        final double score; final String confidence;
        Grade(double s, String c) { score = s; confidence = c; }
    }

    public record Card(Long questionId, String question, String answer, String topic, String difficulty, List<String> tags,
                       Double lastScore, int attempts, boolean bookmarked, String reason) {}
    public record Deck(Scope scope, int available, List<Card> cards) {}
    public record GradeResult(Long questionId, Grade grade, double score, int attempts, int nextReviewInDays) {}

    private final StudyQueries q;
    private final JdbcTemplate jdbc;

    public FlashcardService(StudyQueries q, JdbcTemplate jdbc) { this.q = q; this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public Deck deck(Long topicId, Long companyId, List<Long> tagIds, Scope scope, int limit, boolean answeredOnly) {
        Scope sc = scope == null ? Scope.DUE : scope;
        List<QView> views = q.withState(q.questions(topicId == null ? null : List.of(topicId), companyId, tagIds));
        Instant now = Instant.now();
        List<QView> pool = views.stream()
                .filter(v -> !answeredOnly || (v.q().answer() != null && !v.q().answer().isBlank()))
                .filter(v -> switch (sc) {
                    case NEW -> v.attempts() == 0;
                    case DUE -> v.attempts() == 0 || TopicStudyService.isDue(v, now) || v.bookmarked();
                    case ALL -> true;
                }).toList();
        // priority order, shuffled inside each priority level so repeated decks don't feel identical
        Map<Integer, List<QView>> groups = new TreeMap<>(Comparator.reverseOrder());
        for (QView v : pool) groups.computeIfAbsent(v.priority(), k -> new ArrayList<>()).add(v);
        List<QView> ordered = new ArrayList<>();
        for (List<QView> g : groups.values()) { Collections.shuffle(g, ThreadLocalRandom.current()); ordered.addAll(g); }
        List<QView> picked = ordered.subList(0, Math.min(Math.max(1, Math.min(limit, MAX_DECK)), ordered.size()));
        Map<Long, List<String>> tags = q.tagsOf(picked.stream().map(v -> v.q().id()).toList());
        List<Card> cards = picked.stream().map(v -> new Card(v.q().id(), v.q().text(), v.q().answer(), v.q().topic(), v.q().difficulty(),
                tags.getOrDefault(v.q().id(), List.of()), v.lastScore(), v.attempts(), v.bookmarked(), reason(v, now))).toList();
        return new Deck(sc, pool.size(), cards);
    }

    static String reason(QView v, Instant now) {
        if (v.attempts() == 0) return "New";
        if (v.lastScore() != null && v.lastScore() < PracticeMath.WEAK_SCORE) return "Weak last time";
        if (TopicStudyService.isDue(v, now)) return "Due for review";
        if (v.bookmarked()) return "Bookmarked";
        return "Refresher";
    }

    @Transactional
    public GradeResult grade(Long questionId, Grade grade) {
        if (grade == null) throw new BadRequestException("grade is required");
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM questions WHERE id = ? AND approval_status = 'APPROVED'", Integer.class, questionId);
        if (exists == null || exists == 0) throw new NotFoundException("Question", questionId);
        jdbc.update("INSERT INTO question_attempts (question_id, session_id, user_answer, score, confidence, feedback, attempted_at, mode) " +
                "VALUES (?, NULL, NULL, ?, ?, NULL, ?, 'FLASHCARD')", questionId, grade.score, grade.confidence, Timestamp.from(Instant.now()));
        Integer attempts = jdbc.queryForObject("SELECT COUNT(*) FROM question_attempts WHERE question_id = ?", Integer.class, questionId);
        int n = attempts == null ? 1 : attempts;
        return new GradeResult(questionId, grade, grade.score, n, (int) PracticeMath.revisionInterval(grade.score, n).toDays());
    }
}
