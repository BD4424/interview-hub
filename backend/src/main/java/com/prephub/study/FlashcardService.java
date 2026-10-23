package com.prephub.study;

import com.prephub.common.BadRequestException;
import com.prephub.common.NotFoundException;
import com.prephub.study.FlashcardMath.Grade;
import com.prephub.study.FlashcardMath.Review;
import com.prephub.study.FlashcardMath.State;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Flashcards: quick self-rated recall with their own history (flashcard_reviews) and their own schedule.
 * Reviews are deliberately NOT practice attempts: they never change answered-question counts, scores, revision
 * priority, interview-plan progress or streaks.
 */
@Service
public class FlashcardService {
    static final int MAX_DECK = 100;

    public enum Scope { DUE, NEW, ALL }

    public record Card(Long questionId, String question, String answer, String topic, String difficulty, List<String> tags,
                       Grade lastGrade, int reviews, String reason) {}
    public record Deck(Scope scope, int available, int dueNow, int reviewedToday, List<Card> cards) {}
    public record GradeResult(Long questionId, Grade grade, int reviews, int nextReviewInDays) {}

    private final StudyQueries q;
    private final JdbcTemplate jdbc;
    private final ZoneId zone;

    public FlashcardService(StudyQueries q, JdbcTemplate jdbc, @Value("${prephub.timezone:Asia/Kolkata}") String zone) {
        this.q = q; this.jdbc = jdbc; this.zone = ZoneId.of(zone);
    }

    private Map<Long, State> states() {
        Map<Long, List<Review>> byQ = new HashMap<>();
        jdbc.query("SELECT question_id, grade, reviewed_at FROM flashcard_reviews ORDER BY reviewed_at, id",
                rs -> { byQ.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new Review(Grade.valueOf(rs.getString(2)), rs.getTimestamp(3).toInstant())); });
        Map<Long, State> out = new HashMap<>();
        byQ.forEach((k, v) -> out.put(k, FlashcardMath.state(v)));
        return out;
    }

    @Transactional(readOnly = true)
    public Deck deck(Long topicId, Long companyId, List<Long> tagIds, Scope scope, int limit, boolean answeredOnly) {
        Scope sc = scope == null ? Scope.DUE : scope;
        Instant now = Instant.now();
        Map<Long, State> states = states();
        record Entry(StudyQueries.QRow row, State state, int priority) {}
        List<Entry> all = q.questions(topicId == null ? null : List.of(topicId), companyId, tagIds).stream()
                .filter(r -> !answeredOnly || (r.answer() != null && !r.answer().isBlank()))
                .map(r -> { State s = states.getOrDefault(r.id(), State.NEW); return new Entry(r, s, FlashcardMath.priority(s, now)); })
                .toList();
        List<Entry> pool = all.stream().filter(e -> switch (sc) {
            case NEW -> e.state().reviews() == 0;
            case DUE -> e.priority() > 0;
            case ALL -> true;
        }).toList();
        // priority order, shuffled within a priority level so repeated decks don't feel identical
        Map<Integer, List<Entry>> groups = new TreeMap<>(Comparator.reverseOrder());
        for (Entry e : pool) groups.computeIfAbsent(e.priority(), k -> new ArrayList<>()).add(e);
        List<Entry> ordered = new ArrayList<>();
        for (List<Entry> g : groups.values()) { Collections.shuffle(g, ThreadLocalRandom.current()); ordered.addAll(g); }
        List<Entry> picked = ordered.subList(0, Math.min(Math.max(1, Math.min(limit, MAX_DECK)), ordered.size()));
        Map<Long, List<String>> tags = q.tagsOf(picked.stream().map(e -> e.row().id()).toList());
        List<Card> cards = picked.stream().map(e -> new Card(e.row().id(), e.row().text(), e.row().answer(), e.row().topic(), e.row().difficulty(),
                tags.getOrDefault(e.row().id(), List.of()), e.state().lastGrade(), e.state().reviews(), reason(e.state(), now))).toList();
        Integer today = jdbc.queryForObject("SELECT COUNT(*) FROM flashcard_reviews WHERE reviewed_at >= ?", Integer.class,
                Timestamp.from(LocalDate.now(zone).atStartOfDay(zone).toInstant()));
        int due = (int) all.stream().filter(e -> e.priority() > 0).count();
        return new Deck(sc, pool.size(), due, today == null ? 0 : today, cards);
    }

    static String reason(State s, Instant now) {
        if (s.reviews() == 0) return "New card";
        if (!FlashcardMath.isDue(s, now)) return "Not due yet";
        return switch (s.lastGrade()) { case DIDNT -> "Missed last time"; case SHAKY -> "Shaky last time"; case KNEW -> "Review due"; };
    }

    @Transactional
    public GradeResult grade(Long questionId, Grade grade) {
        if (grade == null) throw new BadRequestException("grade is required");
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM questions WHERE id = ? AND approval_status = 'APPROVED'", Integer.class, questionId);
        if (exists == null || exists == 0) throw new NotFoundException("Question", questionId);
        jdbc.update("INSERT INTO flashcard_reviews (question_id, grade, reviewed_at) VALUES (?, ?, ?)", questionId, grade.name(), Timestamp.from(Instant.now()));
        List<Review> history = jdbc.query("SELECT grade, reviewed_at FROM flashcard_reviews WHERE question_id = ? ORDER BY reviewed_at, id",
                (rs, i) -> new Review(Grade.valueOf(rs.getString(1)), rs.getTimestamp(2).toInstant()), questionId);
        State s = FlashcardMath.state(history);
        return new GradeResult(questionId, grade, s.reviews(), FlashcardMath.intervalDays(grade, s.knewStreak()));
    }
}
