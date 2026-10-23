package com.prephub.study;

import com.prephub.ai.AiTextClient;
import com.prephub.common.BadRequestException;
import com.prephub.common.Enums.Difficulty;
import com.prephub.practice.ProgressService;
import com.prephub.question.QuestionDtos.CompanyAttributionRequest;
import com.prephub.question.QuestionDtos.QuestionRequest;
import com.prephub.question.QuestionService;
import com.prephub.semantic.EmbeddingClient;
import com.prephub.study.FlashcardMath.Grade;
import com.prephub.topic.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class StudyIntegrationTest {
    @MockitoBean AiTextClient ai;
    @MockitoBean EmbeddingClient embeddings;
    @Autowired QuestionService questions;
    @Autowired TopicRepository topics;
    @Autowired TopicStudyService topicStudy;
    @Autowired FlashcardService flashcards;
    @Autowired PlanService plans;
    @Autowired WeeklyReportService weekly;
    @Autowired ProgressService progress;
    @Autowired JdbcTemplate jdbc;

    Long java, kafka, q1, q2, q3, k1, amazon, tagCollections, tagConcurrency;

    @BeforeEach void setUp() {
        for (String t : List.of("mock_test_items", "mock_tests", "flashcard_reviews", "interview_plan_topics", "interview_plans", "learning_path_steps",
                "question_attempts", "question_bookmarks", "interview_turns", "interview_sessions", "question_embeddings", "question_relations",
                "import_candidates", "import_jobs", "question_tags", "question_companies", "questions", "tags", "companies"))
            jdbc.update("DELETE FROM " + t);
        when(ai.available()).thenReturn(false);
        when(embeddings.available()).thenReturn(false);
        when(embeddings.status()).thenReturn("off");
        java = topics.findBySlug("java").orElseThrow().getId();
        kafka = topics.findBySlug("kafka").orElseThrow().getId();
        var a = questions.create(req("Explain HashMap internals", "Buckets", java, List.of("Collections"), List.of(co("Amazon"))));
        q1 = a.id(); amazon = a.companies().get(0).companyId();
        q2 = questions.create(req("What is a ConcurrentHashMap?", "Segments/bins", java, List.of("Collections", "Concurrency"), List.of())).id();
        q3 = questions.create(req("What is volatile?", null, java, List.of("Concurrency"), List.of())).id();
        k1 = questions.create(req("What is a consumer group?", "Group of consumers", kafka, List.of(), List.of(co("Amazon")))).id();
        tagCollections = jdbc.queryForObject("SELECT id FROM tags WHERE normalized_name = 'collections'", Long.class);
        tagConcurrency = jdbc.queryForObject("SELECT id FROM tags WHERE normalized_name = 'concurrency'", Long.class);
    }

    private static QuestionRequest req(String text, String answer, Long topic, List<String> tags, List<CompanyAttributionRequest> cos) {
        return new QuestionRequest(text, answer, topic, tags, Difficulty.INTERMEDIATE, null, null, null, null, cos);
    }
    private static CompanyAttributionRequest co(String n) { return new CompanyAttributionRequest(null, n, null, null, null, null, null, null, null); }

    /** A graded practice answer (what practice sessions and mock tests record). */
    private void attempt(Long questionId, double score) {
        jdbc.update("INSERT INTO question_attempts (question_id, user_answer, score, attempted_at, mode) VALUES (?, 'x', ?, ?, 'PRACTICE')",
                questionId, score, Timestamp.from(Instant.now()));
    }

    @Test void topicOverviewNotesAndPath() {
        attempt(q1, 9);
        attempt(q2, 2);
        var o = topicStudy.overview(java);
        assertThat(o.totalQuestions()).isEqualTo(3);
        assertThat(o.practiced()).isEqualTo(2);
        assertThat(o.currentScore()).isEqualTo(5.5);
        assertThat(o.mastery()).isEqualTo(36.7);
        assertThat(o.weakest().get(0).questionId()).isEqualTo(q2);
        assertThat(o.neverPracticed()).extracting(TopicStudyService.QItem::questionId).containsExactly(q3);
        assertThat(o.byCompany()).extracting(TopicStudyService.Bucket::label).containsExactly("Amazon");
        assertThat(o.dueForRevision()).isEqualTo(1);
        assertThat(topicStudy.saveNotes(java, "# HashMap\n- load factor 0.75").notes()).startsWith("# HashMap");

        var custom = topicStudy.savePath(java, List.of(tagConcurrency, tagCollections));
        assertThat(custom.custom()).isTrue();
        assertThat(custom.steps()).extracting(TopicStudyService.Step::tagName).containsExactly("Concurrency", "Collections");
        assertThat(custom.nextTagId()).isEqualTo(tagConcurrency);
        assertThat(topicStudy.savePath(java, List.of()).custom()).isFalse();
    }

    @Test void flashcardsKeepTheirOwnHistoryAndNeverCountAsPractice() {
        assertThat(flashcards.deck(java, null, null, FlashcardService.Scope.DUE, 10, false).cards()).hasSize(3);   // all new
        assertThat(flashcards.grade(q1, Grade.KNEW).nextReviewInDays()).isEqualTo(7);
        assertThat(flashcards.grade(q2, Grade.DIDNT).nextReviewInDays()).isEqualTo(1);

        var due = flashcards.deck(java, null, null, FlashcardService.Scope.DUE, 10, false);
        assertThat(due.cards()).extracting(FlashcardService.Card::questionId).containsExactly(q3);      // others are scheduled later
        assertThat(due.reviewedToday()).isEqualTo(2);
        assertThat(flashcards.deck(java, null, null, FlashcardService.Scope.ALL, 10, true).cards()).hasSize(2);   // q3 has no answer

        // separation: no attempts, no streak, no practised questions, no plan progress, not "answers" in the weekly report
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question_attempts", Integer.class)).isZero();
        assertThat(progress.progress().summary().streakDays()).isZero();
        assertThat(topicStudy.overview(java).practiced()).isZero();
        var w = weekly.report();
        assertThat(w.thisWeek().answers()).isZero();
        assertThat(w.thisWeek().flashcards()).isEqualTo(2);
    }

    @Test void planUsesCompanyQuestionsAndSchedules() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        var d = plans.create(new PlanService.PlanRequest(null, amazon, today.plusDays(3), List.of(), null, null));
        assertThat(d.plan().title()).isEqualTo("Amazon interview");
        assertThat(d.plan().questions()).isEqualTo(2);
        assertThat(d.perDay()).isEqualTo(1);

        flashcards.grade(d.today().get(0).questionId(), Grade.KNEW);           // does not tick the plan
        assertThat(plans.detail(d.plan().id()).plan().todayDone()).isZero();
        attempt(d.today().get(0).questionId(), 9);
        var after = plans.detail(d.plan().id());
        assertThat(after.today()).anyMatch(PlanService.PlanItem::done);
        assertThat(after.plan().readiness()).isEqualTo(45.0);

        var narrowed = plans.update(d.plan().id(), new PlanService.PlanRequest("Amazon SDE2", amazon, today.plusDays(3), List.of(kafka), 5, null));
        assertThat(narrowed.plan().questions()).isEqualTo(1);
        assertThatThrownBy(() -> plans.create(new PlanService.PlanRequest("x", null, today.minusDays(1), List.of(), null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test void weeklyReport() {
        attempt(q1, 5);
        var w = weekly.report();
        assertThat(w.thisWeek().answers()).isEqualTo(1);
        assertThat(w.weakest()).extracting(WeeklyReportService.Weak::questionId).containsExactly(q1);
        assertThat(w.lastWeek().answers()).isZero();
        assertThat(w.thisWeek().mockTests()).isZero();
    }
}
