package com.prephub.practice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prephub.ai.AiDtos;
import com.prephub.ai.AiQuestionService;
import com.prephub.common.BadRequestException;
import com.prephub.common.Enums.Difficulty;
import com.prephub.common.NotFoundException;
import com.prephub.company.CompanyRepository;
import com.prephub.practice.EvaluationValidator.Evaluation;
import com.prephub.practice.PracticeDtos.*;
import com.prephub.practice.PracticeEnums.*;
import com.prephub.question.Question;
import com.prephub.question.QuestionCompany;
import com.prephub.question.QuestionRepository;
import com.prephub.tag.Tag;
import com.prephub.tag.TagRepository;
import com.prephub.topic.TopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Practice sessions. Model calls run outside transactions. Scores land in question_attempts only for bank questions;
 * session-only follow-ups (question_id = null) are kept on the turn and never attributed to a company.
 */
@Service
public class PracticeService {
    static final int DEFAULT_LIMIT = 10, MOCK_LIMIT = 6, MAX_FOLLOW_UP_DEPTH = 2;

    private final SessionRepository sessions;
    private final TurnRepository turns;
    private final AttemptRepository attempts;
    private final PracticeStats stats;
    private final AnswerEvaluator evaluator;
    private final AiQuestionService aiQuestions;
    private final QuestionRepository questions;
    private final TopicRepository topics;
    private final CompanyRepository companies;
    private final TagRepository tags;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public PracticeService(SessionRepository sessions, TurnRepository turns, AttemptRepository attempts, PracticeStats stats,
                           AnswerEvaluator evaluator, AiQuestionService aiQuestions, QuestionRepository questions,
                           TopicRepository topics, CompanyRepository companies, TagRepository tags, ObjectMapper json,
                           TransactionTemplate tx) {
        this.sessions = sessions; this.turns = turns; this.attempts = attempts; this.stats = stats; this.evaluator = evaluator;
        this.aiQuestions = aiQuestions; this.questions = questions; this.topics = topics; this.companies = companies;
        this.tags = tags; this.json = json; this.tx = tx;
    }

    // ------------------------------------------------------------------ start

    public SessionDto start(StartSessionRequest r) {
        SessionType type = r.type();
        switch (type) {
            case TOPIC -> { if (r.topicId() == null) throw new BadRequestException("Choose a topic"); }
            case COMPANY -> { if (r.companyId() == null) throw new BadRequestException("Choose a company"); }
            case TAG -> { if (r.tagIds() == null || r.tagIds().isEmpty()) throw new BadRequestException("Choose at least one tag"); }
            case SINGLE -> { if (r.questionId() == null) throw new BadRequestException("questionId is required"); }
            default -> { }
        }
        Long id = tx.execute(s -> {
            if (r.topicId() != null && !topics.existsById(r.topicId())) throw new NotFoundException("Topic", r.topicId());
            if (r.companyId() != null && !companies.existsById(r.companyId())) throw new NotFoundException("Company", r.companyId());
            InterviewSession ses = new InterviewSession();
            ses.setType(type);
            ses.setTopicId(type == SessionType.COMPANY || type == SessionType.TAG ? null : r.topicId());
            ses.setCompanyId(type == SessionType.COMPANY ? r.companyId() : null);
            ses.setTagIdList(type == SessionType.TAG ? r.tagIds() : List.of());
            ses.setDifficulty(type == SessionType.MOCK || type == SessionType.REVISION ? null : r.difficulty());
            ses.setSeedQuestionId(type == SessionType.SINGLE ? r.questionId() : null);
            int limit = r.questionLimit() != null ? r.questionLimit() : type == SessionType.MOCK ? MOCK_LIMIT : DEFAULT_LIMIT;
            ses.setQuestionLimit(type == SessionType.SINGLE ? 1 : limit);
            ses = sessions.save(ses);
            PracticeStats.Candidate first = type == SessionType.SINGLE ? seed(r.questionId()) : pick(ses, List.of(), r.difficulty());
            if (first == null) throw new BadRequestException("No approved questions match this practice selection yet");
            addBankTurn(ses, first, 1);
            return ses.getId();
        });
        return get(id, null);
    }

    private PracticeStats.Candidate seed(Long questionId) {
        Question q = questions.findById(questionId).orElseThrow(() -> new NotFoundException("Question", questionId));
        return new PracticeStats.Candidate(q.getId(), q.getDifficulty(), q.getTopic().getName());
    }

    /** Chooses the next bank question according to the session type. */
    PracticeStats.Candidate pick(InterviewSession s, List<InterviewTurn> history, Difficulty mockStart) {
        Set<Long> asked = history.stream().map(InterviewTurn::getQuestionId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<PracticeStats.Candidate> cands = stats.candidates(s.getTopicId(), s.getCompanyId(), s.tagIdList(), s.getDifficulty())
                .stream().filter(c -> !asked.contains(c.id())).toList();
        if (cands.isEmpty()) return null;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (s.getType() == SessionType.REVISION) {
            Map<Long, PracticeStats.QStat> st = stats.questionStats(null);
            Set<Long> bookmarked = stats.bookmarkedIds();
            Instant now = Instant.now();
            int best = -1;
            List<PracticeStats.Candidate> top = new ArrayList<>();
            for (var c : cands) {
                var q = st.get(c.id());
                int p = PracticeMath.revisionPriority(q == null ? 0 : q.attempts(), q == null ? null : q.lastScore(),
                        q == null ? null : q.lastAttempt(), bookmarked.contains(c.id()), now);
                if (p > best) { best = p; top.clear(); }
                if (p == best) top.add(c);
            }
            return top.get(rnd.nextInt(top.size()));
        }
        if (s.getType() == SessionType.MOCK) {
            List<InterviewTurn> bank = history.stream().filter(t -> t.getType() == TurnType.BANK).toList();
            Difficulty current = bank.isEmpty() ? mockStart : bank.get(bank.size() - 1).getDifficulty();
            List<Double> scores = history.stream().filter(t -> t.getScore() != null).map(InterviewTurn::getScore).toList();
            Difficulty target = bank.isEmpty() ? (mockStart == null ? Difficulty.INTERMEDIATE : mockStart) : PracticeMath.nextDifficulty(current, scores);
            List<PracticeStats.Candidate> atTarget = cands.stream().filter(c -> c.difficulty() == target).toList();
            if (!atTarget.isEmpty()) return atTarget.get(rnd.nextInt(atTarget.size()));
        }
        return cands.get(rnd.nextInt(cands.size()));
    }

    private void addBankTurn(InterviewSession s, PracticeStats.Candidate c, int number) {
        Question q = questions.findById(c.id()).orElseThrow(() -> new NotFoundException("Question", c.id()));
        InterviewTurn t = new InterviewTurn();
        t.setSessionId(s.getId());
        t.setQuestionId(q.getId());
        t.setQuestionText(q.getQuestionText());
        t.setTurnNumber(number);
        t.setType(TurnType.BANK);
        t.setDifficulty(q.getDifficulty());
        t.setTopicName(q.getTopic().getName());
        turns.save(t);
    }

    private void addFollowUpTurn(InterviewSession s, InterviewTurn parent, String question, List<String> points, Difficulty d, int number) {
        InterviewTurn t = new InterviewTurn();
        t.setSessionId(s.getId());
        t.setQuestionId(null);                                     // session-only: never a bank question, never attributed
        t.setQuestionText(question);
        t.setTurnNumber(number);
        t.setType(TurnType.FOLLOW_UP);
        t.setParentTurnId(parent.getId());
        t.setExpectedPoints(points == null || points.isEmpty() ? null : String.join("\n", points));
        t.setDifficulty(d != null ? d : parent.getDifficulty());
        t.setTopicName(parent.getTopicName());
        turns.save(t);
    }

    // ------------------------------------------------------------------ answer

    private record AnswerContext(Long turnId, String question, String topic, String difficulty, String reference, List<String> points) {}

    public SessionDto answer(Long sessionId, Long turnId, AnswerRequest r) {
        AnswerContext ctx = tx.execute(s -> {
            InterviewTurn t = activeTurn(sessionId, turnId, TurnStatus.ASKED);
            String ref = t.getQuestionId() == null ? null : questions.findById(t.getQuestionId()).map(Question::getAnswerText).orElse(null);
            return new AnswerContext(t.getId(), t.getQuestionText(), t.getTopicName(), t.getDifficulty() == null ? null : t.getDifficulty().name(),
                    ref, splitPoints(t.getExpectedPoints()));
        });
        Evaluation eval = null;
        Double score = r.selfScore();
        TurnStatus status;
        if (score != null) status = TurnStatus.EVALUATED;
        else if (evaluator.available()) {
            eval = evaluator.evaluate(ctx.question(), ctx.topic(), ctx.difficulty(), ctx.reference(), ctx.points(), r.answer());
            score = eval.score();
            status = TurnStatus.EVALUATED;
        } else status = TurnStatus.AWAITING_SELF_SCORE;

        final Evaluation fe = eval; final Double fs = score;
        tx.executeWithoutResult(s -> {
            InterviewTurn t = activeTurn(sessionId, turnId, TurnStatus.ASKED);
            t.setUserAnswer(r.answer().trim());
            t.setConfidence(r.confidence());
            t.setAnsweredAt(Instant.now());
            t.setStatus(status);
            t.setScore(fs == null ? null : EvaluationValidator.roundHalf(fs));
            t.setFeedbackJson(fe == null ? null : write(fe));
            if (status == TurnStatus.EVALUATED) recordAttempt(t);
        });
        return get(sessionId, null);
    }

    public SessionDto selfScore(Long sessionId, Long turnId, SelfScoreRequest r) {
        tx.executeWithoutResult(s -> {
            InterviewTurn t = activeTurn(sessionId, turnId, TurnStatus.AWAITING_SELF_SCORE);
            t.setScore(EvaluationValidator.roundHalf(r.score()));
            t.setStatus(TurnStatus.EVALUATED);
            recordAttempt(t);
        });
        return get(sessionId, null);
    }

    public SessionDto skip(Long sessionId, Long turnId) {
        tx.executeWithoutResult(s -> {
            InterviewTurn t = activeTurn(sessionId, turnId, TurnStatus.ASKED);
            t.setStatus(TurnStatus.SKIPPED);
            t.setAnsweredAt(Instant.now());
        });
        return get(sessionId, null);
    }

    private void recordAttempt(InterviewTurn t) {
        if (t.getQuestionId() == null || t.getScore() == null) return;
        QuestionAttempt a = new QuestionAttempt();
        a.setQuestionId(t.getQuestionId());
        a.setSessionId(t.getSessionId());
        a.setUserAnswer(t.getUserAnswer());
        a.setScore(t.getScore());
        a.setConfidence(t.getConfidence());
        a.setFeedback(t.getFeedbackJson());
        attempts.save(a);
    }

    private InterviewTurn activeTurn(Long sessionId, Long turnId, TurnStatus expected) {
        InterviewSession s = session(sessionId);
        if (s.getStatus() != SessionStatus.ACTIVE) throw new BadRequestException("This session is already completed");
        InterviewTurn t = turns.findById(turnId).orElseThrow(() -> new NotFoundException("Turn", turnId));
        if (!t.getSessionId().equals(sessionId)) throw new NotFoundException("Turn", turnId);
        if (t.getStatus() != expected) throw new BadRequestException("This question is " + t.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        return t;
    }

    // ------------------------------------------------------------------ follow-ups & next

    /** User-requested follow-up on an evaluated turn: from the evaluation, else generated by the AI (session-only). */
    public SessionDto followUp(Long sessionId, Long turnId) {
        record Parent(InterviewTurn turn, Evaluation eval, int depth, List<String> asked) {}
        Parent p = tx.execute(s -> {
            InterviewTurn t = activeTurn(sessionId, turnId, TurnStatus.EVALUATED);
            if (!turns.findByParentTurnId(turnId).isEmpty()) throw new BadRequestException("A follow-up was already asked for this question");
            int depth = depth(t);
            if (depth >= MAX_FOLLOW_UP_DEPTH) throw new BadRequestException("Follow-up chain limit reached; move to the next question");
            List<String> asked = turns.findBySessionIdOrderByTurnNumberAsc(sessionId).stream().map(InterviewTurn::getQuestionText).toList();
            return new Parent(t, read(t.getFeedbackJson()), depth, asked);
        });
        String question; List<String> points; Difficulty d = null;
        if (p.eval() != null && p.eval().followUpQuestion() != null) {
            question = p.eval().followUpQuestion().question();
            points = p.eval().followUpQuestion().expectedAnswerPoints();
        } else if (p.turn().getQuestionId() != null && evaluator.available()) {
            var batch = aiQuestions.generateFollowUps(p.turn().getQuestionId(), new AiDtos.GenerateFollowUpsRequest(1,
                    AiDtos.FollowUpMode.SESSION_ONLY, null, null, null, p.turn().getUserAnswer(), p.asked()));
            var item = batch.items().get(0);
            question = item.question(); points = item.expectedAnswerPoints(); d = item.difficulty();
        } else {
            throw new BadRequestException("No follow-up available (AI is not configured)");
        }
        final Difficulty fd = d;
        tx.executeWithoutResult(s -> {
            InterviewSession ses = session(sessionId);
            if (!turns.findByParentTurnId(turnId).isEmpty()) return;
            addFollowUpTurn(ses, p.turn(), question, points, fd, nextNumber(sessionId));
        });
        return get(sessionId, null);
    }

    public SessionDto next(Long sessionId) {
        String message = tx.execute(s -> {
            InterviewSession ses = session(sessionId);
            if (ses.getStatus() != SessionStatus.ACTIVE) throw new BadRequestException("This session is already completed");
            List<InterviewTurn> history = turns.findBySessionIdOrderByTurnNumberAsc(sessionId);
            if (history.stream().anyMatch(t -> t.getStatus() == TurnStatus.ASKED || t.getStatus() == TurnStatus.AWAITING_SELF_SCORE))
                throw new BadRequestException("Answer or skip the current question first");
            InterviewTurn last = history.get(history.size() - 1);
            if (ses.getType() == SessionType.MOCK && last.getStatus() == TurnStatus.EVALUATED && turns.findByParentTurnId(last.getId()).isEmpty()) {
                Evaluation e = read(last.getFeedbackJson());
                boolean has = e != null && e.followUpQuestion() != null;
                if (PracticeMath.mockShouldFollowUp(last.getScore(), has, depth(last))) {
                    addFollowUpTurn(ses, last, e.followUpQuestion().question(), e.followUpQuestion().expectedAnswerPoints(), null, history.size() + 1);
                    return "Follow-up on your last answer";
                }
            }
            long bank = history.stream().filter(t -> t.getType() == TurnType.BANK).count();
            if (bank >= ses.getQuestionLimit()) { finish(ses, history); return "Session complete"; }
            PracticeStats.Candidate c = pick(ses, history, null);
            if (c == null) { finish(ses, history); return "No more matching questions — session complete"; }
            addBankTurn(ses, c, history.size() + 1);
            return null;
        });
        return get(sessionId, message);
    }

    public SessionDto complete(Long sessionId) {
        tx.executeWithoutResult(s -> {
            InterviewSession ses = session(sessionId);
            if (ses.getStatus() == SessionStatus.COMPLETED) return;
            finish(ses, turns.findBySessionIdOrderByTurnNumberAsc(sessionId));
        });
        return get(sessionId, "Session complete");
    }

    private void finish(InterviewSession s, List<InterviewTurn> history) {
        // unanswered questions are recorded as skipped so the summary is accurate
        history.stream().filter(t -> t.getStatus() == TurnStatus.ASKED).forEach(t -> t.setStatus(TurnStatus.SKIPPED));
        OptionalDouble avg = history.stream().filter(t -> t.getScore() != null).mapToDouble(InterviewTurn::getScore).average();
        s.setOverallScore(avg.isPresent() ? Math.round(avg.getAsDouble() * 10) / 10.0 : null);
        s.setStatus(SessionStatus.COMPLETED);
        s.setCompletedAt(Instant.now());
    }

    @Transactional
    public void delete(Long sessionId) { sessions.delete(session(sessionId)); }

    private int depth(InterviewTurn t) {
        int d = 0;
        InterviewTurn cur = t;
        while (cur.getParentTurnId() != null && d < 10) {
            d++;
            cur = turns.findById(cur.getParentTurnId()).orElse(null);
            if (cur == null) break;
        }
        return d;
    }

    private int nextNumber(Long sessionId) {
        return turns.findBySessionIdOrderByTurnNumberAsc(sessionId).stream().mapToInt(InterviewTurn::getTurnNumber).max().orElse(0) + 1;
    }

    // ------------------------------------------------------------------ read

    private InterviewSession session(Long id) { return sessions.findById(id).orElseThrow(() -> new NotFoundException("Session", id)); }

    public SessionDto get(Long id, String message) {
        return tx.execute(s -> toDto(session(id), message));
    }

    @Transactional(readOnly = true)
    public List<SessionSummary> recent() {
        return sessions.findTop20ByOrderByStartedAtDesc().stream().map(this::summary).toList();
    }

    SessionSummary summary(InterviewSession s) {
        int answered = (int) turns.findBySessionIdOrderByTurnNumberAsc(s.getId()).stream().filter(t -> t.getStatus() == TurnStatus.EVALUATED).count();
        return new SessionSummary(s.getId(), s.getType(), s.getStatus(), title(s), answered, s.getOverallScore(), s.getStartedAt(), s.getCompletedAt());
    }

    private String title(InterviewSession s) {
        String topic = s.getTopicId() == null ? null : topics.findById(s.getTopicId()).map(t -> t.getName()).orElse(null);
        return switch (s.getType()) {
            case TOPIC -> "Topic · " + topic;
            case COMPANY -> "Company · " + (s.getCompanyId() == null ? "?" : companies.findById(s.getCompanyId()).map(c -> c.getName()).orElse("?"));
            case TAG -> "Tags · " + tags.findAllById(s.tagIdList()).stream().map(Tag::getName).collect(Collectors.joining(", "));
            case RANDOM -> "Random" + (topic != null ? " · " + topic : "");
            case MOCK -> "Mock interview" + (topic != null ? " · " + topic : "");
            case REVISION -> "Revision" + (topic != null ? " · " + topic : "");
            case SINGLE -> "Single question";
        };
    }

    private SessionDto toDto(InterviewSession s, String message) {
        List<InterviewTurn> history = turns.findBySessionIdOrderByTurnNumberAsc(s.getId());
        Map<Long, Question> qs = questions.findAllById(history.stream().map(InterviewTurn::getQuestionId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        Set<Long> parents = history.stream().map(InterviewTurn::getParentTurnId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<TurnDto> dtos = new ArrayList<>();
        Long current = null;
        for (InterviewTurn t : history) {
            boolean revealed = t.getStatus() != TurnStatus.ASKED;
            Question q = t.getQuestionId() == null ? null : qs.get(t.getQuestionId());
            Evaluation e = read(t.getFeedbackJson());
            boolean canFollow = s.getStatus() == SessionStatus.ACTIVE && t.getStatus() == TurnStatus.EVALUATED && !parents.contains(t.getId())
                    && depth(t) < MAX_FOLLOW_UP_DEPTH && ((e != null && e.followUpQuestion() != null) || (t.getQuestionId() != null && evaluator.available()));
            dtos.add(new TurnDto(t.getId(), t.getTurnNumber(), t.getType(), t.getStatus(), t.getQuestionId(), t.getQuestionText(),
                    t.getTopicName(), t.getDifficulty(),
                    q == null ? List.of() : q.getTags().stream().map(Tag::getName).toList(),
                    q == null ? List.of() : q.getCompanies().stream().map(QuestionCompany::getCompany).map(c -> c.getName()).toList(),
                    t.getType() == TurnType.FOLLOW_UP, t.getParentTurnId(),
                    t.getUserAnswer(), t.getConfidence(), t.getScore(), e,
                    revealed && q != null ? q.getAnswerText() : null,
                    revealed ? splitPoints(t.getExpectedPoints()) : List.of(),
                    canFollow, t.getAskedAt(), t.getAnsweredAt()));
            if (t.getStatus() == TurnStatus.ASKED || t.getStatus() == TurnStatus.AWAITING_SELF_SCORE) current = t.getId();
        }
        int bank = (int) history.stream().filter(t -> t.getType() == TurnType.BANK).count();
        String topic = s.getTopicId() == null ? null : topics.findById(s.getTopicId()).map(t -> t.getName()).orElse(null);
        String company = s.getCompanyId() == null ? null : companies.findById(s.getCompanyId()).map(c -> c.getName()).orElse(null);
        List<String> tagNames = tags.findAllById(s.tagIdList()).stream().map(Tag::getName).toList();
        return new SessionDto(s.getId(), s.getType(), s.getStatus(), title(s), topic, company, tagNames, s.getDifficulty(),
                s.getQuestionLimit(), bank, s.getStartedAt(), s.getCompletedAt(), s.getOverallScore(), evaluator.available(),
                current, dtos, message);
    }

    // ------------------------------------------------------------------ attempts

    @Transactional(readOnly = true)
    public List<AttemptDto> attemptsFor(Long questionId) {
        if (!questions.existsById(questionId)) throw new NotFoundException("Question", questionId);
        return attempts.findByQuestionIdOrderByAttemptedAtDesc(questionId).stream().map(a -> new AttemptDto(a.getId(), a.getSessionId(),
                a.getUserAnswer(), a.getScore(), a.getConfidence(), read(a.getFeedback()), a.getAttemptedAt())).toList();
    }

    // ------------------------------------------------------------------ json helpers

    private String write(Evaluation e) {
        try { return json.writeValueAsString(e); } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }

    Evaluation read(String s) {
        if (s == null || s.isBlank()) return null;
        try { return json.readValue(s, Evaluation.class); } catch (Exception ex) { return null; }
    }

    static List<String> splitPoints(String s) {
        return s == null || s.isBlank() ? List.of() : Arrays.stream(s.split("\n")).filter(x -> !x.isBlank()).toList();
    }
}
