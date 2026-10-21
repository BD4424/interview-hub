package com.prephub.study;

import com.prephub.common.BadRequestException;
import com.prephub.common.NotFoundException;
import com.prephub.common.TextNormalizer;
import com.prephub.study.StudyQueries.QView;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Interview countdown plans. The schedule is recomputed from your current practice state every time it is viewed, so
 * practising (or skipping a day) automatically re-balances the remaining days.
 */
@Service
public class PlanService {
    private final JdbcTemplate jdbc;
    private final StudyQueries q;
    private final ZoneId zone;

    public PlanService(JdbcTemplate jdbc, StudyQueries q, @Value("${prephub.timezone:Asia/Kolkata}") String zone) {
        this.jdbc = jdbc; this.q = q; this.zone = ZoneId.of(zone);
    }

    public record PlanRequest(@Size(max = 200) String title, Long companyId, @NotNull LocalDate interviewDate,
                              @Size(max = 30) List<Long> topicIds, @Min(1) @Max(50) Integer dailyTarget, @Size(max = 2000) String notes) {}

    public enum Phase { UPCOMING, TODAY, PAST }
    public record PlanItem(Long questionId, String question, String topic, String difficulty, Double lastScore, int attempts, boolean done) {}
    public record PlanDay(LocalDate date, List<PlanItem> items) {}
    public record TopicReadiness(Long topicId, String topic, int questions, int practiced, Double readiness) {}
    public record PlanSummary(Long id, String title, Long companyId, String company, LocalDate interviewDate, int daysLeft, Phase phase,
                              List<Long> topicIds, List<String> topics, Integer dailyTarget, String notes, int questions, int practiced,
                              Double readiness, int todayDone, int todayTarget, Instant createdAt) {}
    public record PlanDetail(PlanSummary plan, String scope, int perDay, int pendingTotal, List<PlanItem> today,
                             List<PlanDay> upcoming, int unscheduled, List<TopicReadiness> byTopic) {}

    private record Row(Long id, String title, Long companyId, String company, LocalDate date, Integer dailyTarget, String notes, Instant createdAt) {}

    private List<Row> rows(String where, Object... args) {
        return jdbc.query("SELECT p.id, p.title, p.company_id, c.name, p.interview_date, p.daily_target, p.notes, p.created_at FROM interview_plans p " +
                        "LEFT JOIN companies c ON c.id = p.company_id WHERE p.status = 'ACTIVE' " + where,
                (rs, i) -> new Row(rs.getLong(1), rs.getString(2), (Long) rs.getObject(3, Long.class), rs.getString(4), rs.getDate(5).toLocalDate(),
                        (Integer) rs.getObject(6, Integer.class), rs.getString(7), rs.getTimestamp(8).toInstant()), args);
    }

    private Row row(Long id) {
        List<Row> r = rows("AND p.id = ?", id);
        if (r.isEmpty()) throw new NotFoundException("Plan", id);
        return r.get(0);
    }

    private Map<Long, String> topicsOf(Long planId) {
        Map<Long, String> m = new LinkedHashMap<>();
        jdbc.query("SELECT t.id, t.name FROM interview_plan_topics pt JOIN topics t ON t.id = pt.topic_id WHERE pt.plan_id = ? ORDER BY t.name",
                rs -> { m.put(rs.getLong(1), rs.getString(2)); }, planId);
        return m;
    }

    // ------------------------------------------------------------------ write

    @Transactional
    public PlanDetail create(PlanRequest r) {
        validate(r);
        String title = title(r);
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO interview_plans (title, company_id, interview_date, daily_target, notes, status, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setObject(2, r.companyId());
            ps.setDate(3, Date.valueOf(r.interviewDate()));
            ps.setObject(4, r.dailyTarget());
            ps.setString(5, blank(r.notes()));
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            return ps;
        }, kh);
        Long id = Objects.requireNonNull(kh.getKey()).longValue();
        saveTopics(id, r.topicIds());
        return detail(id);
    }

    @Transactional
    public PlanDetail update(Long id, PlanRequest r) {
        row(id);
        validate(r);
        jdbc.update("UPDATE interview_plans SET title = ?, company_id = ?, interview_date = ?, daily_target = ?, notes = ? WHERE id = ?",
                title(r), r.companyId(), Date.valueOf(r.interviewDate()), r.dailyTarget(), blank(r.notes()), id);
        jdbc.update("DELETE FROM interview_plan_topics WHERE plan_id = ?", id);
        saveTopics(id, r.topicIds());
        return detail(id);
    }

    @Transactional
    public void delete(Long id) { row(id); jdbc.update("DELETE FROM interview_plans WHERE id = ?", id); }

    private void validate(PlanRequest r) {
        if (r.interviewDate().isBefore(LocalDate.now(zone))) throw new BadRequestException("The interview date is in the past");
        if (r.companyId() != null) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM companies WHERE id = ?", Integer.class, r.companyId());
            if (n == null || n == 0) throw new NotFoundException("Company", r.companyId());
        }
        if (r.companyId() == null && (r.topicIds() == null || r.topicIds().isEmpty()) && TextNormalizer.cleanDisplay(r.title()) == null)
            throw new BadRequestException("Give the plan a title, a company or at least one topic");
    }

    private String title(PlanRequest r) {
        String t = TextNormalizer.cleanDisplay(r.title());
        if (t != null) return t;
        if (r.companyId() != null) return jdbc.queryForObject("SELECT name FROM companies WHERE id = ?", String.class, r.companyId()) + " interview";
        return "Interview";
    }

    private void saveTopics(Long planId, List<Long> topicIds) {
        for (Long t : topicIds == null ? List.<Long>of() : topicIds.stream().filter(Objects::nonNull).distinct().toList()) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM topics WHERE id = ?", Integer.class, t);
            if (n == null || n == 0) throw new NotFoundException("Topic", t);
            jdbc.update("INSERT INTO interview_plan_topics (plan_id, topic_id) VALUES (?, ?)", planId, t);
        }
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    // ------------------------------------------------------------------ read

    private record Scope(List<QView> views, String description) {}

    /** Company questions (narrowed to the plan's topics); falls back to topic questions when the company has none there. */
    private Scope scope(Row p, Map<Long, String> topics) {
        if (p.companyId() != null) {
            List<QView> v = q.withState(q.questions(topics.keySet(), p.companyId(), null));
            if (!v.isEmpty()) return new Scope(v, "Questions you attributed to " + p.company() + (topics.isEmpty() ? "" : " in " + String.join(", ", topics.values())));
            if (!topics.isEmpty())
                return new Scope(q.withState(q.questions(topics.keySet(), null, null)),
                        "No questions attributed to " + p.company() + " in these topics yet, so all " + String.join(", ", topics.values()) + " questions are used");
            return new Scope(List.of(), "No questions are attributed to " + p.company() + " yet. Add topics to the plan or attribute questions to the company");
        }
        if (!topics.isEmpty()) return new Scope(q.withState(q.questions(topics.keySet(), null, null)), "All questions in " + String.join(", ", topics.values()));
        return new Scope(q.withState(q.questions(null, null, null)), "Your whole question bank");
    }

    @Transactional(readOnly = true)
    public List<PlanSummary> list() {
        return rows("ORDER BY p.interview_date, p.id").stream().map(r -> build(r, false).plan()).toList();
    }

    /** Nearest plan whose interview is today or later (for the dashboard countdown). */
    @Transactional(readOnly = true)
    public Optional<PlanSummary> upcoming() {
        return rows("AND p.interview_date >= ? ORDER BY p.interview_date, p.id", Date.valueOf(LocalDate.now(zone))).stream()
                .findFirst().map(r -> build(r, false).plan());
    }

    @Transactional(readOnly = true)
    public PlanDetail detail(Long id) { return build(row(id), true); }

    private PlanDetail build(Row p, boolean full) {
        LocalDate today = LocalDate.now(zone);
        Map<Long, String> topics = topicsOf(p.id());
        Scope sc = scope(p, topics);
        List<StudyMath.QState> states = sc.views().stream().map(QView::state).toList();
        Set<Long> doneTodaySet = q.attemptedSince(today.atStartOfDay(zone).toInstant());
        Map<Long, QView> byId = sc.views().stream().collect(Collectors.toMap(v -> v.q().id(), Function.identity()));
        List<Long> doneToday = sc.views().stream().map(v -> v.q().id()).filter(doneTodaySet::contains).toList();
        List<Long> pending = StudyQueries.byPriority(sc.views()).stream().filter(v -> v.priority() > 0).map(v -> v.q().id()).toList();
        StudyMath.Schedule s = StudyMath.schedule(pending, doneToday, today, p.date(), p.dailyTarget());

        int daysLeft = StudyMath.daysLeft(today, p.date());
        Phase phase = p.date().isBefore(today) ? Phase.PAST : p.date().isEqual(today) ? Phase.TODAY : Phase.UPCOMING;
        PlanSummary summary = new PlanSummary(p.id(), p.title(), p.companyId(), p.company(), p.date(), daysLeft, phase,
                List.copyOf(topics.keySet()), List.copyOf(topics.values()), p.dailyTarget(), p.notes(), states.size(), StudyMath.practised(states),
                StudyMath.mastery(states), doneToday.size(), s.todayDone().size() + s.todayPending().size(), p.createdAt());
        if (!full) return new PlanDetail(summary, sc.description(), s.perDay(), pending.size(), List.of(), List.of(), 0, List.of());

        List<PlanItem> todayItems = new ArrayList<>();
        s.todayDone().forEach(id -> todayItems.add(item(byId.get(id), true)));
        s.todayPending().forEach(id -> todayItems.add(item(byId.get(id), false)));
        List<PlanDay> days = s.upcoming().stream().limit(14)
                .map(d -> new PlanDay(d.date(), d.questionIds().stream().map(id -> item(byId.get(id), false)).toList())).toList();
        int scheduled = s.todayPending().size() + s.upcoming().stream().mapToInt(d -> d.questionIds().size()).sum();
        int unscheduled = Math.max(0, pending.stream().filter(id -> !doneTodaySet.contains(id)).toList().size() - scheduled);

        Map<String, List<QView>> perTopic = sc.views().stream().collect(Collectors.groupingBy(v -> v.q().topic(), TreeMap::new, Collectors.toList()));
        List<TopicReadiness> byTopic = perTopic.values().stream().map(vs -> {
            List<StudyMath.QState> st = vs.stream().map(QView::state).toList();
            return new TopicReadiness(vs.get(0).q().topicId(), vs.get(0).q().topic(), st.size(), StudyMath.practised(st), StudyMath.mastery(st));
        }).sorted(Comparator.comparing(t -> t.readiness() == null ? 0 : t.readiness())).toList();
        return new PlanDetail(summary, sc.description(), s.perDay(), pending.size(), todayItems, days, unscheduled, byTopic);
    }

    private static PlanItem item(QView v, boolean done) {
        return new PlanItem(v.q().id(), v.q().text(), v.q().topic(), v.q().difficulty(), v.lastScore(), v.attempts(), done);
    }
}
