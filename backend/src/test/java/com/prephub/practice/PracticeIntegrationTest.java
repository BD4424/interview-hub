package com.prephub.practice;

import com.prephub.ai.AiExceptions;
import com.prephub.ai.AiTextClient;
import com.prephub.common.BadRequestException;
import com.prephub.common.Enums.Difficulty;
import com.prephub.practice.PracticeDtos.*;
import com.prephub.practice.PracticeEnums.*;
import com.prephub.question.QuestionDtos.CompanyAttributionRequest;
import com.prephub.question.QuestionDtos.QuestionRequest;
import com.prephub.question.QuestionService;
import com.prephub.semantic.EmbeddingClient;
import com.prephub.topic.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
class PracticeIntegrationTest {
    @MockitoBean AiTextClient ai;
    @MockitoBean EmbeddingClient embeddings;
    @Autowired PracticeService practice;
    @Autowired BookmarkService bookmarks;
    @Autowired ProgressService progress;
    @Autowired QuestionService questions;
    @Autowired AttemptRepository attempts;
    @Autowired TopicRepository topics;
    @Autowired JdbcTemplate jdbc;

    Long java, q1, q2, q3, amazon;

    static String eval(double score, boolean followUp) {
        return "{\"score\":" + score + ",\"rubric\":{\"technicalCorrectness\":" + score + ",\"clarity\":8},"
                + "\"summary\":\"ok\",\"correctPoints\":[\"buckets\"],\"missedPoints\":[\"treeify\"],\"incorrectStatements\":[],"
                + "\"improvedAnswer\":\"Model answer\",\"revisionTopics\":[\"HashMap\"]"
                + (followUp ? ",\"followUpQuestion\":{\"question\":\"What happens when a HashMap bucket gets too long?\",\"expectedAnswerPoints\":[\"treeify at 8\"]}" : "")
                + "}";
    }

    @BeforeEach void setUp() {
        for (String t : List.of("question_attempts", "question_bookmarks", "interview_turns", "interview_sessions", "question_embeddings",
                "question_relations", "import_candidates", "import_jobs", "question_tags", "question_companies", "questions", "tags", "companies"))
            jdbc.update("DELETE FROM " + t);
        when(embeddings.available()).thenReturn(false);
        when(embeddings.status()).thenReturn("off");
        when(ai.available()).thenReturn(true);
        when(ai.provider()).thenReturn("openai");
        when(ai.model()).thenReturn("test");
        when(ai.complete(anyString(), anyString())).thenReturn(eval(6.5, true));
        java = topics.findBySlug("java").orElseThrow().getId();
        var a = questions.create(new QuestionRequest("Explain HashMap internals", "Reference answer", java, List.of("HashMap"), Difficulty.INTERMEDIATE,
                null, null, null, null, List.of(new CompanyAttributionRequest(null, "Amazon", null, null, null, null, null, null, null))));
        q1 = a.id(); amazon = a.companies().get(0).companyId();
        q2 = questions.create(new QuestionRequest("What is the JVM?", null, java, List.of(), Difficulty.BEGINNER, null, null, null, null, List.of())).id();
        q3 = questions.create(new QuestionRequest("Explain G1 garbage collection", null, java, List.of(), Difficulty.ADVANCED, null, null, null, null, List.of())).id();
    }

    private static TurnDto current(SessionDto s) { return s.turns().stream().filter(t -> t.id().equals(s.currentTurnId())).findFirst().orElseThrow(); }

    @Test void topicSessionWithEvaluationFollowUpSelfScoreAndCompletion() {
        SessionDto s = practice.start(new StartSessionRequest(SessionType.TOPIC, java, null, null, null, 2, null));
        TurnDto t1 = current(s);
        assertThat(t1.referenceAnswer()).isNull();                         // not revealed before answering
        assertThat(t1.expectedPoints()).isEmpty();

        s = practice.answer(s.id(), t1.id(), new AnswerRequest("Buckets and hashing", Confidence.MEDIUM, null));
        TurnDto done = s.turns().get(0);
        assertThat(done.status()).isEqualTo(TurnStatus.EVALUATED);
        assertThat(done.score()).isEqualTo(6.5);
        assertThat(done.evaluation().improvedAnswer()).isEqualTo("Model answer");
        assertThat(done.followUpAvailable()).isTrue();
        assertThat(attempts.count()).isEqualTo(1);

        s = practice.followUp(s.id(), done.id());
        TurnDto fu = current(s);
        assertThat(fu.type()).isEqualTo(TurnType.FOLLOW_UP);
        assertThat(fu.questionId()).isNull();
        assertThat(fu.sessionOnly()).isTrue();
        assertThat(fu.companies()).isEmpty();                              // never attributed, even if the parent is an Amazon question
        assertThatThrownBy(() -> practice.next(s0(fu))).isInstanceOf(BadRequestException.class);

        s = practice.answer(s.id(), fu.id(), new AnswerRequest("It becomes a tree", null, null));
        assertThat(attempts.count()).isEqualTo(1);                         // follow-ups are not bank attempts
        assertThat(current(practice.next(s.id())).type()).isEqualTo(TurnType.BANK);

        s = practice.get(s.id(), null);
        TurnDto t2 = current(s);
        long aiCalls = completeCalls();
        s = practice.answer(s.id(), t2.id(), new AnswerRequest("Not sure", Confidence.LOW, 3.0));
        assertThat(completeCalls()).isEqualTo(aiCalls);                    // self-score: no AI call
        assertThat(attempts.count()).isEqualTo(2);

        s = practice.next(s.id());
        assertThat(s.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(s.overallScore()).isEqualTo(5.3);                       // (6.5 + 6.5 + 3) / 3

        var p = progress.progress();
        assertThat(p.summary().totalAttempts()).isEqualTo(2);
        assertThat(p.summary().todayAttempts()).isEqualTo(2);
        assertThat(p.summary().streakDays()).isEqualTo(1);
        assertThat(p.topics()).extracting(TopicProgress::topic).containsExactly("Java");
    }

    private long completeCalls() {
        return mockingDetails(ai).getInvocations().stream().filter(i -> i.getMethod().getName().equals("complete")).count();
    }
    private Long s0(TurnDto t) { return jdbc.queryForObject("SELECT session_id FROM interview_turns WHERE id = ?", Long.class, t.id()); }

    @Test void withoutAiTheAnswerIsStoredThenSelfScored() {
        when(ai.available()).thenReturn(false);
        SessionDto s = practice.start(new StartSessionRequest(SessionType.RANDOM, null, null, null, null, 1, null));
        TurnDto t = current(s);
        s = practice.answer(s.id(), t.id(), new AnswerRequest("My answer", null, null));
        TurnDto awaiting = current(s);
        assertThat(awaiting.status()).isEqualTo(TurnStatus.AWAITING_SELF_SCORE);
        assertThat(attempts.count()).isZero();
        s = practice.selfScore(s.id(), t.id(), new SelfScoreRequest(7.0));
        assertThat(s.turns().get(0).status()).isEqualTo(TurnStatus.EVALUATED);
        assertThat(attempts.count()).isEqualTo(1);
        verify(ai, never()).complete(anyString(), anyString());
    }

    @Test void invalidAiOutputLeavesTheQuestionOpen() {
        when(ai.complete(anyString(), anyString())).thenReturn("{\"score\": 42}");
        SessionDto s = practice.start(new StartSessionRequest(SessionType.RANDOM, null, null, null, null, 1, null));
        Long turn = s.currentTurnId();
        assertThatThrownBy(() -> practice.answer(s.id(), turn, new AnswerRequest("x", null, null))).isInstanceOf(AiExceptions.AiOutputException.class);
        assertThat(current(practice.get(s.id(), null)).status()).isEqualTo(TurnStatus.ASKED);
        verify(ai, times(2)).complete(anyString(), anyString());
    }

    @Test void companyPracticeOnlyUsesAttributedQuestions() {
        SessionDto s = practice.start(new StartSessionRequest(SessionType.COMPANY, null, amazon, null, null, 5, null));
        assertThat(current(s).questionId()).isEqualTo(q1);
        s = practice.answer(s.id(), s.currentTurnId(), new AnswerRequest("a", null, 8.0));
        s = practice.next(s.id());
        assertThat(s.status()).isEqualTo(SessionStatus.COMPLETED);         // only one Amazon question exists
    }

    @Test void mockInterviewAsksAFollowUpAfterAPartlyRightAnswer() {
        SessionDto s = practice.start(new StartSessionRequest(SessionType.MOCK, java, null, null, null, 3, null));
        s = practice.answer(s.id(), s.currentTurnId(), new AnswerRequest("partial", null, null));
        s = practice.next(s.id());
        assertThat(current(s).type()).isEqualTo(TurnType.FOLLOW_UP);
        assertThat(s.message()).contains("Follow-up");
    }

    @Test void bookmarksStatusAndRevisionPriority() {
        bookmarks.save(q3, "review GC");
        assertThat(bookmarks.list()).extracting(BookmarkService.BookmarkDto::questionId).containsExactly(q3);
        // q2 answered badly earlier -> highest revision priority alongside bookmarks
        SessionDto s = practice.start(new StartSessionRequest(SessionType.SINGLE, null, null, null, null, null, q2));
        practice.answer(s.id(), s.currentTurnId(), new AnswerRequest("dunno", null, 1.0));
        var st = bookmarks.status(List.of(q1, q2, q3));
        assertThat(st.get(1).attempts()).isEqualTo(1);
        assertThat(st.get(1).dueForRevision()).isTrue();
        assertThat(st.get(2).bookmarked()).isTrue();

        SessionDto rev = practice.start(new StartSessionRequest(SessionType.REVISION, null, null, null, null, 3, null));
        assertThat(current(rev).questionId()).isEqualTo(q2);               // weak answer (6) beats bookmarked + never attempted (5)
        bookmarks.remove(q3);
        assertThat(bookmarks.list()).isEmpty();
    }
}
