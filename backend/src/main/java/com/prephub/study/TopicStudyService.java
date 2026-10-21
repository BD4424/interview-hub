package com.prephub.study;

import com.prephub.common.BadRequestException;
import com.prephub.common.NotFoundException;
import com.prephub.practice.PracticeMath;
import com.prephub.study.StudyMath.StepStatus;
import com.prephub.study.StudyQueries.QView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Topic pages: overview, study notes and the tag-based learning path. */
@Service
public class TopicStudyService {
    static final int MAX_NOTES = 100_000, MAX_STEPS = 30, AUTO_STEPS = 8, LIST = 8;

    private final JdbcTemplate jdbc;
    private final StudyQueries q;

    public TopicStudyService(JdbcTemplate jdbc, StudyQueries q) { this.jdbc = jdbc; this.q = q; }

    public record Bucket(String label, long count) {}
    public record QItem(Long questionId, String question, String difficulty, Double lastScore, int attempts, boolean bookmarked) {}
    public record Overview(Long id, String name, String slug, String description, String notes, Instant notesUpdatedAt,
                           int totalQuestions, int answered, int practiced, Double coverage, Double currentScore, Double mastery,
                           int dueForRevision, List<Bucket> byDifficulty, List<Bucket> byTag, List<Bucket> byCompany,
                           List<QItem> weakest, List<QItem> neverPracticed, int neverPracticedCount) {}

    public record Step(Long tagId, String tagName, int position, int questionCount, int practiced, Double mastery,
                       Double currentScore, StepStatus status) {}
    public record TagOption(Long tagId, String tagName, int questionCount) {}
    public record Path(Long topicId, boolean custom, double masteredThreshold, List<Step> steps, Long nextTagId,
                       Double overallMastery, List<TagOption> availableTags) {}

    private record TopicRow(Long id, String name, String slug, String description, String notes, Instant notesUpdatedAt) {}

    private TopicRow topic(Long id) {
        List<TopicRow> r = jdbc.query("SELECT id, name, slug, description, notes, notes_updated_at FROM topics WHERE id = ?",
                (rs, i) -> new TopicRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()), id);
        if (r.isEmpty()) throw new NotFoundException("Topic", id);
        return r.get(0);
    }

    @Transactional(readOnly = true)
    public Overview overview(Long id) {
        TopicRow t = topic(id);
        List<QView> views = q.withState(q.questions(List.of(id), null, null));
        List<StudyMath.QState> states = views.stream().map(QView::state).toList();
        int total = views.size(), practiced = StudyMath.practised(states);
        int answered = (int) views.stream().filter(v -> v.q().answer() != null && !v.q().answer().isBlank()).count();

        Map<String, Long> diff = views.stream().collect(Collectors.groupingBy(v -> v.q().difficulty() == null ? "UNSPECIFIED" : v.q().difficulty(), TreeMap::new, Collectors.counting()));
        List<Bucket> byTag = jdbc.query("SELECT t.name, COUNT(*) FROM question_tags qt JOIN tags t ON t.id = qt.tag_id JOIN questions q ON q.id = qt.question_id " +
                "WHERE q.topic_id = ? AND q.approval_status = 'APPROVED' GROUP BY t.name ORDER BY COUNT(*) DESC, t.name", (rs, i) -> new Bucket(rs.getString(1), rs.getLong(2)), id);
        List<Bucket> byCompany = jdbc.query("SELECT c.name, COUNT(*) FROM question_companies qc JOIN companies c ON c.id = qc.company_id JOIN questions q ON q.id = qc.question_id " +
                "WHERE q.topic_id = ? AND q.approval_status = 'APPROVED' GROUP BY c.name ORDER BY COUNT(*) DESC, c.name", (rs, i) -> new Bucket(rs.getString(1), rs.getLong(2)), id);

        List<QItem> weakest = views.stream().filter(v -> v.lastScore() != null)
                .sorted(Comparator.comparingDouble(QView::lastScore).thenComparing(v -> v.q().id())).limit(LIST).map(TopicStudyService::item).toList();
        List<QView> never = views.stream().filter(v -> v.attempts() == 0).toList();
        Instant now = Instant.now();
        int due = (int) views.stream().filter(v -> isDue(v, now)).count();
        return new Overview(t.id(), t.name(), t.slug(), t.description(), t.notes(), t.notesUpdatedAt(), total, answered, practiced,
                total == 0 ? null : StudyMath.round1(practiced * 100.0 / total), StudyMath.currentScore(states), StudyMath.mastery(states), due,
                diff.entrySet().stream().map(e -> new Bucket(e.getKey(), e.getValue())).toList(), byTag, byCompany,
                weakest, never.stream().limit(LIST).map(TopicStudyService::item).toList(), never.size());
    }

    static boolean isDue(QView v, Instant now) {
        return v.lastScore() != null && (v.lastScore() < PracticeMath.WEAK_SCORE || PracticeMath.isDue(v.lastAttempt(), v.lastScore(), v.attempts(), now));
    }

    private static QItem item(QView v) {
        return new QItem(v.q().id(), v.q().text(), v.q().difficulty(), v.lastScore(), v.attempts(), v.bookmarked());
    }

    @Transactional
    public Overview saveNotes(Long id, String notes) {
        topic(id);
        String n = notes == null || notes.isBlank() ? null : notes;
        if (n != null && n.length() > MAX_NOTES) throw new BadRequestException("Notes are limited to " + MAX_NOTES + " characters");
        jdbc.update("UPDATE topics SET notes = ?, notes_updated_at = ? WHERE id = ?", n, Timestamp.from(Instant.now()), id);
        return overview(id);
    }

    // ------------------------------------------------------------------ learning path

    @Transactional(readOnly = true)
    public Path path(Long topicId) {
        topic(topicId);
        List<QView> views = q.withState(q.questions(List.of(topicId), null, null));
        Map<Long, QView> byId = views.stream().collect(Collectors.toMap(v -> v.q().id(), v -> v));
        Map<Long, String> tagNames = new HashMap<>();
        Map<Long, List<Long>> questionsByTag = new LinkedHashMap<>();
        if (!byId.isEmpty())
            jdbc.query("SELECT qt.tag_id, t.name, qt.question_id FROM question_tags qt JOIN tags t ON t.id = qt.tag_id WHERE qt.question_id IN (" + StudyQueries.ph(byId.size()) + ")",
                    rs -> { tagNames.put(rs.getLong(1), rs.getString(2)); questionsByTag.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(3)); },
                    byId.keySet().toArray());

        List<long[]> custom = jdbc.query("SELECT tag_id, step_order FROM learning_path_steps WHERE topic_id = ? ORDER BY step_order, id",
                (rs, i) -> new long[]{rs.getLong(1), rs.getInt(2)}, topicId);
        List<Long> order;
        boolean isCustom = !custom.isEmpty();
        if (isCustom) {
            order = custom.stream().map(a -> a[0]).toList();
            for (Long tagId : order) if (!tagNames.containsKey(tagId))
                tagNames.put(tagId, jdbc.queryForObject("SELECT name FROM tags WHERE id = ?", String.class, tagId));
        } else {
            order = questionsByTag.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<Long, List<Long>>>comparingInt(e -> e.getValue().size()).reversed().thenComparing(e -> tagNames.get(e.getKey())))
                    .limit(AUTO_STEPS).map(Map.Entry::getKey).toList();
        }

        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            Long tagId = order.get(i);
            List<StudyMath.QState> st = questionsByTag.getOrDefault(tagId, List.of()).stream().map(byId::get).filter(Objects::nonNull).map(QView::state).toList();
            Double m = StudyMath.mastery(st);
            int p = StudyMath.practised(st);
            steps.add(new Step(tagId, tagNames.get(tagId), i + 1, st.size(), p, m, StudyMath.currentScore(st), StudyMath.status(m, p, st.size())));
        }
        int next = StudyMath.nextStep(steps.stream().map(Step::status).toList());
        Set<Long> inPath = new HashSet<>(order);
        List<TagOption> available = questionsByTag.entrySet().stream().filter(e -> !inPath.contains(e.getKey()))
                .map(e -> new TagOption(e.getKey(), tagNames.get(e.getKey()), e.getValue().size()))
                .sorted(Comparator.comparingInt(TagOption::questionCount).reversed().thenComparing(TagOption::tagName)).toList();
        return new Path(topicId, isCustom, StudyMath.MASTERED, steps, next < 0 ? null : steps.get(next).tagId(),
                StudyMath.mastery(views.stream().map(QView::state).toList()), available);
    }

    @Transactional
    public Path savePath(Long topicId, List<Long> tagIds) {
        topic(topicId);
        List<Long> ids = tagIds == null ? List.of() : tagIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() > MAX_STEPS) throw new BadRequestException("A learning path can have at most " + MAX_STEPS + " steps");
        for (Long t : ids) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tags WHERE id = ?", Integer.class, t);
            if (n == null || n == 0) throw new NotFoundException("Tag", t);
        }
        jdbc.update("DELETE FROM learning_path_steps WHERE topic_id = ?", topicId);
        for (int i = 0; i < ids.size(); i++)
            jdbc.update("INSERT INTO learning_path_steps (topic_id, tag_id, step_order) VALUES (?, ?, ?)", topicId, ids.get(i), i + 1);
        return path(topicId);
    }
}
