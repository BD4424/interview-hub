package com.prephub.study;

import com.prephub.ai.AiTextClient;
import com.prephub.common.BadRequestException;
import com.prephub.common.Enums.Difficulty;
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
    @Autowired JdbcTemplate jdbc;

    Long java, kafka, q1, q2, q3, k1, amazon, tagCollections, tagConcurrency;

    @BeforeEach void setUp() {
        for (String t : List.of("interview_plan_topics", "interview_plans", "learning_path_steps", "question_attempts", "question_bookmarks",
                "interview_turns", "interview_sessions", "question_embeddings", "question_relations", "import_candidates", "import_jobs",
                "question_tags", "question_companies", "questions", "tags", "companies"))
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

    @Test void topicOverviewNotesAndPath() {
        flashcards.grade(q1, FlashcardService.Grade.KNEW);
        flashcards.grade(q2, FlashcardService.Grade.DIDNT);
        var o = topicStudy.overview(java);
        assertThat(o.totalQuestions()).isEqualTo(3);
        assertThat(o.practiced()).isEqualTo(2);
        assertThat(o.currentScore()).isEqualTo(5.5);
        assertThat(o.mastery()).isEqualTo(36.7);                // (0.9 + 0.2 + 0) / 3
        assertThat(o.weakest().get(0).questionId()).isEqualTo(q2);
        assertThat(o.neverPracticed()).extracting(TopicStudyService.QItem::questionId).containsExactly(q3);
        assertThat(o.byCompany()).extracting(TopicStudyService.Bucket::label).containsExactly("Amazon");
        assertThat(o.dueForRevision()).isEqualTo(1);

        assertThat(topicStudy.saveNotes(java, "# HashMap\n- load factor 0.75").notes()).startsWith("# HashMap");

        var auto = topicStudy.path(java);
        assertThat(auto.custom()).isFalse();
        assertThat(auto.steps()).extracting(TopicStudyService.Step::tagName).containsExactlyInAnyOrder("Collections", "Concurrency");

        var custom = topicStudy.savePath(java, List.of(tagConcurrency, tagCollections));
        assertThat(custom.custom()).isTrue();
        assertThat(custom.steps()).extracting(TopicStudyService.Step::tagName).containsExactly("Concurrency", "Collections");
        assertThat(custom.nextTagId()).isEqualTo(tagConcurrency);   // 10% mastery: in progress
        assertThat(topicStudy.savePath(java, List.of()).custom()).isFalse();
    }

    @Test void flashcardDeckPrioritisesAndGradesFeedRevision() {
        var deck = flashcards.deck(java, null, null, FlashcardService.Scope.DUE, 10, false);
        assertThat(deck.cards()).hasSize(3);                      // all new
        flashcards.grade(q1, FlashcardService.Grade.KNEW);
        var r = flashcards.grade(q2, FlashcardService.Grade.DIDNT);
        assertThat(r.nextReviewInDays()).isEqualTo(1);
        var due = flashcards.deck(java, null, null, FlashcardService.Scope.DUE, 10, false);
        assertThat(due.cards()).extracting(FlashcardService.Card::questionId).containsExactly(q2, q3);   // weak (6+...) before new (2)
        assertThat(flashcards.deck(java, null, null, FlashcardService.Scope.ALL, 10, true).cards()).hasSize(2);  // q3 has no answer
        String mode = jdbc.queryForObject("SELECT mode FROM question_attempts WHERE question_id = ?", String.class, q1);
        assertThat(mode).isEqualTo("FLASHCARD");
    }

    @Test void planUsesCompanyQuestionsAndSchedules() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        var d = plans.create(new PlanService.PlanRequest(null, amazon, today.plusDays(3), List.of(), null, null));
        assertThat(d.plan().title()).isEqualTo("Amazon interview");
        assertThat(d.plan().questions()).isEqualTo(2);              // only Amazon-attributed questions
        assertThat(d.plan().daysLeft()).isEqualTo(3);
        assertThat(d.perDay()).isEqualTo(1);
        assertThat(d.today()).hasSize(1);
        assertThat(d.upcoming()).hasSize(1);

        flashcards.grade(d.today().get(0).questionId(), FlashcardService.Grade.KNEW);
        var after = plans.detail(d.plan().id());
        assertThat(after.today()).anyMatch(PlanService.PlanItem::done);
        assertThat(after.plan().readiness()).isEqualTo(45.0);

        var narrowed = plans.update(d.plan().id(), new PlanService.PlanRequest("Amazon SDE2", amazon, today.plusDays(3), List.of(kafka), 5, null));
        assertThat(narrowed.plan().questions()).isEqualTo(1);
        assertThat(plans.upcoming()).isPresent();
        assertThatThrownBy(() -> plans.create(new PlanService.PlanRequest("x", null, today.minusDays(1), List.of(), null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test void weeklyReport() {
        flashcards.grade(q1, FlashcardService.Grade.SHAKY);
        var w = weekly.report();
        assertThat(w.thisWeek().answers()).isEqualTo(1);
        assertThat(w.thisWeek().flashcards()).isEqualTo(1);
        assertThat(w.weakest()).extracting(WeeklyReportService.Weak::questionId).containsExactly(q1);
        assertThat(w.lastWeek().answers()).isZero();
    }
}
