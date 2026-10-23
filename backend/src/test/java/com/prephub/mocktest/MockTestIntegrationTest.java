package com.prephub.mocktest;

import com.prephub.ai.AiTextClient;
import com.prephub.common.BadRequestException;
import com.prephub.common.Enums.Difficulty;
import com.prephub.mocktest.MockTestService.*;
import com.prephub.practice.ProgressService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"prephub.mock-test.async=false",
        "prephub.mock-test.sweep-initial-delay-ms=3600000", "prephub.mock-test.sweep-interval-ms=3600000"})
class MockTestIntegrationTest {
    @MockitoBean AiTextClient ai;
    @MockitoBean EmbeddingClient embeddings;
    @Autowired MockTestService tests;
    @Autowired QuestionService questions;
    @Autowired TopicRepository topics;
    @Autowired ProgressService progress;
    @Autowired JdbcTemplate jdbc;

    Long java, kafka;

    static String eval(double score) {
        return "{\"score\":" + score + ",\"summary\":\"ok\",\"correctPoints\":[\"a\"],\"missedPoints\":[\"b\"],\"incorrectStatements\":[],"
                + "\"improvedAnswer\":\"Model answer\",\"revisionTopics\":[\"HashMap\"]}";
    }

    @BeforeEach void setUp() {
        for (String t : List.of("mock_test_items", "mock_tests", "flashcard_reviews", "interview_plan_topics", "interview_plans", "learning_path_steps",
                "question_attempts", "question_bookmarks", "interview_turns", "interview_sessions", "question_embeddings", "question_relations",
                "import_candidates", "import_jobs", "question_tags", "question_companies", "questions", "tags", "companies"))
            jdbc.update("DELETE FROM " + t);
        when(embeddings.available()).thenReturn(false);
        when(embeddings.status()).thenReturn("off");
        when(ai.available()).thenReturn(true);
        when(ai.provider()).thenReturn("openai");
        when(ai.model()).thenReturn("test");
        when(ai.complete(anyString(), anyString())).thenReturn(eval(7.0));
        java = topics.findBySlug("java").orElseThrow().getId();
        kafka = topics.findBySlug("kafka").orElseThrow().getId();
        q("Explain HashMap internals", java, Difficulty.BEGINNER, "Buckets");
        q("What is a ConcurrentHashMap?", java, Difficulty.INTERMEDIATE, null);
        q("Explain G1 garbage collection", java, Difficulty.ADVANCED, null);
        q("What is a consumer group?", kafka, Difficulty.INTERMEDIATE, "Group of consumers sharing partitions");
        q("How does Kafka guarantee ordering?", kafka, Difficulty.ADVANCED, null);
    }

    private void q(String text, Long topic, Difficulty d, String answer) {
        questions.create(new QuestionRequest(text, answer, topic, List.of(), d, null, null, null, null, List.of()));
    }

    private int mockAttempts() { return jdbc.queryForObject("SELECT COUNT(*) FROM question_attempts WHERE mode = 'MOCK_TEST'", Integer.class); }

    @Test void fullMockIsBalancedHiddenUntilSubmittedAndScoredAtTheEnd() {
        TestDetail t = tests.create(new CreateRequest(Scope.FULL, null, null, 4, null, null, null));
        assertThat(t.items()).hasSize(4);
        assertThat(t.items().stream().filter(i -> "Kafka".equals(i.topic())).count()).isEqualTo(2);
        assertThat(t.title()).isEqualTo("Full mock · 4 questions");
        for (int i = 0; i < 3; i++) tests.save(t.id(), t.items().get(i).id(), new AnswerRequest("my answer " + i, null));
        assertThat(tests.save(t.id(), t.items().get(0).id(), new AnswerRequest(null, true)).flagged()).isTrue();

        TestDetail mid = tests.get(t.id());
        assertThat(mid.answeredCount()).isEqualTo(3);
        assertThat(mid.items()).allMatch(i -> i.score() == null && i.referenceAnswer() == null && i.evaluation() == null);
        verify(ai, never()).complete(anyString(), anyString());          // nothing graded while the test is running

        TestDetail done = tests.submit(t.id());
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.results().overallScore()).isEqualTo(5.3);      // (7 + 7 + 7 + 0) / 4 = 5.25
        assertThat(done.results().percent()).isEqualTo(52.5);
        assertThat(done.results().unanswered()).isEqualTo(1);
        assertThat(done.results().revisionTopics()).containsExactly("HashMap");
        assertThat(done.items().get(0).evaluation().improvedAnswer()).isEqualTo("Model answer");
        verify(ai, times(3)).complete(anyString(), anyString());

        assertThat(mockAttempts()).isEqualTo(3);                         // counts as practice...
        assertThat(progress.progress().summary().streakDays()).isEqualTo(1);   // ...and towards the streak
        assertThatThrownBy(() -> tests.save(t.id(), t.items().get(3).id(), new AnswerRequest("late", null))).isInstanceOf(BadRequestException.class);
        assertThat(tests.submit(t.id()).status()).isEqualTo("COMPLETED"); // idempotent
        assertThat(mockAttempts()).isEqualTo(3);
    }

    @Test void singleTopicWithoutAiIsSelfScored() {
        when(ai.available()).thenReturn(false);
        TestDetail t = tests.create(new CreateRequest(Scope.TOPICS, List.of(kafka), null, 10, 30, null, null));
        assertThat(t.items()).hasSize(2).allMatch(i -> "Kafka".equals(i.topic()));
        assertThat(t.remainingSeconds()).isBetween(1790L, 1800L);
        var first = t.items().get(0);
        tests.save(t.id(), first.id(), new AnswerRequest("partitions", null));
        TestDetail sub = tests.submit(t.id());
        assertThat(sub.status()).isEqualTo("AWAITING_SELF_SCORE");
        assertThat(sub.items()).anyMatch(i -> i.referenceAnswer() != null);
        assertThatThrownBy(() -> tests.selfScore(t.id(), new SelfScoreRequest(Map.of()))).isInstanceOf(BadRequestException.class);
        TestDetail done = tests.selfScore(t.id(), new SelfScoreRequest(Map.of(first.id(), 8.0)));
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.results().overallScore()).isEqualTo(4.0);
        assertThat(mockAttempts()).isEqualTo(1);
    }

    @Test void failedGradingCanBeRetried() {
        when(ai.complete(anyString(), anyString())).thenReturn("not json");
        TestDetail t = tests.create(new CreateRequest(Scope.TOPICS, List.of(kafka), null, 2, null, null, null));
        t.items().forEach(i -> tests.save(t.id(), i.id(), new AnswerRequest("answer", null)));
        TestDetail failed = tests.submit(t.id());
        assertThat(failed.status()).isEqualTo("COMPLETED");
        assertThat(failed.results().failed()).isEqualTo(2);
        assertThat(failed.results().overallScore()).isNull();
        assertThat(failed.items()).allMatch(i -> i.error() != null);
        assertThat(mockAttempts()).isZero();

        when(ai.complete(anyString(), anyString())).thenReturn(eval(6.0));
        TestDetail retried = tests.retryFailed(t.id());
        assertThat(retried.status()).isEqualTo("COMPLETED");
        assertThat(retried.results().overallScore()).isEqualTo(6.0);
        assertThat(mockAttempts()).isEqualTo(2);
    }

    @Test void expiredTestIsSubmittedAutomatically() {
        TestDetail t = tests.create(new CreateRequest(Scope.FULL, null, null, 2, 10, null, null));
        jdbc.update("UPDATE mock_tests SET deadline_at = ? WHERE id = ?", Timestamp.from(Instant.now().minusSeconds(600)), t.id());
        assertThatThrownBy(() -> tests.save(t.id(), t.items().get(0).id(), new AnswerRequest("too late", null))).isInstanceOf(BadRequestException.class);
        TestDetail after = tests.get(t.id());
        assertThat(after.status()).isEqualTo("COMPLETED");
        assertThat(after.results().overallScore()).isEqualTo(0.0);
    }

    @Test void retakeWeakCreatesCustomTest() {
        when(ai.complete(anyString(), anyString())).thenReturn(eval(9.0));
        TestDetail t = tests.create(new CreateRequest(Scope.TOPICS, List.of(java), null, 3, null, null, null));
        tests.save(t.id(), t.items().get(0).id(), new AnswerRequest("good answer", null));
        tests.submit(t.id());
        TestDetail weak = tests.retake(t.id(), new RetakeRequest(true, null));
        assertThat(weak.scope()).isEqualTo("CUSTOM");
        assertThat(weak.items()).hasSize(2);
        assertThat(weak.title()).startsWith("Retake weak");
        assertThat(tests.retake(t.id(), new RetakeRequest(false, 15)).items()).hasSize(3);
        assertThat(tests.list()).hasSize(3);
        assertThat(tests.available(Scope.TOPICS, List.of(java), Difficulty.ADVANCED).available()).isEqualTo(1);
    }
}
