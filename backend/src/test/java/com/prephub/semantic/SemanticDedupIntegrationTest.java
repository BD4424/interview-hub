package com.prephub.semantic;

import com.prephub.common.Enums.*;
import com.prephub.importer.ImportDtos.CandidateDto;
import com.prephub.importer.ImportDtos.ResolveRequest;
import com.prephub.importer.ImportService;
import com.prephub.question.QuestionDtos.QuestionRequest;
import com.prephub.question.QuestionRepository;
import com.prephub.question.QuestionService;
import com.prephub.topic.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@SpringBootTest
class SemanticDedupIntegrationTest {
    @MockitoBean EmbeddingClient embeddings;
    @Autowired EmbeddingIndexer indexer;
    @Autowired ImportService imports;
    @Autowired QuestionService questions;
    @Autowired QuestionRepository repo;
    @Autowired TopicRepository topics;
    @Autowired DedupController dedup;
    @Autowired JdbcTemplate jdbc;

    Long existing;

    @BeforeEach void setUp() {
        for (String t : List.of("question_attempts", "question_bookmarks", "interview_turns", "interview_sessions", "question_embeddings",
                "question_relations", "import_candidates", "import_jobs", "question_tags", "question_companies", "questions", "tags", "companies"))
            jdbc.update("DELETE FROM " + t);
        when(embeddings.available()).thenReturn(true);
        when(embeddings.model()).thenReturn("fake-bow");
        when(embeddings.embed(anyList())).thenAnswer(inv -> FakeEmbeddings.embedAll(inv.getArgument(0)));
        indexer.reset();
        Long java = topics.findBySlug("java").orElseThrow().getId();
        existing = questions.create(new QuestionRequest("Explain the internal working of HashMap in Java", "Buckets...", java,
                List.of(), null, null, null, null, null, List.of())).id();
        indexer.syncNow();
    }

    @Test void importFlagsSemanticMatchesForReviewAndKeepsRelatedQuestionsSeparate() {
        String csv = "question\n"
                + "Explain internal working of Java HashMap\n"        // same words → likely duplicate
                + "Explain internal working of HashMap\n"             // 4 of 5 words → possible duplicate
                + "Explain ConcurrentHashMap locking in Java\n";      // related topic, different question → new
        var job = imports.start(new ImportService.Upload("s.csv", csv.getBytes(StandardCharsets.UTF_8)), AttributionMode.NONE, null, null, List.of(), false);
        Map<String, CandidateDto> byQ = imports.candidates(job.id(), null).stream().collect(Collectors.toMap(CandidateDto::question, Function.identity()));
        CandidateDto likely = byQ.get("Explain internal working of Java HashMap");
        CandidateDto possible = byQ.get("Explain internal working of HashMap");
        CandidateDto fresh = byQ.get("Explain ConcurrentHashMap locking in Java");
        assertThat(likely.matchType()).isEqualTo(MatchType.LIKELY_SEMANTIC_DUPLICATE);
        assertThat(likely.matchedQuestionId()).isEqualTo(existing);
        assertThat(likely.similarityScore()).isGreaterThanOrEqualTo(0.9);
        assertThat(possible.matchType()).isEqualTo(MatchType.POSSIBLE_DUPLICATE);
        assertThat(possible.reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
        assertThat(fresh.matchType()).isEqualTo(MatchType.NEW_CANDIDATE);

        imports.approve(job.id(), List.of());                          // imports only the new one
        assertThat(repo.count()).isEqualTo(2);
        imports.resolve(job.id(), possible.id(), new ResolveRequest(ReviewAction.KEEP_SEPARATE, null));
        imports.resolve(job.id(), likely.id(), new ResolveRequest(ReviewAction.MERGE, null));
        assertThat(repo.count()).isEqualTo(3);
        assertThat(imports.get(job.id()).status()).isEqualTo(ImportStatus.COMPLETED);
    }

    @Test void checkEndpointAndIndexStatus() {
        var r = dedup.check(new DedupController.CheckRequest("Explain internal working of Java HashMap", null));
        assertThat(r.exact()).isNull();
        assertThat(r.similar()).extracting(DedupController.Match::questionId).containsExactly(existing);
        var self = dedup.check(new DedupController.CheckRequest("Explain the internal working of HashMap in Java", existing));
        assertThat(self.exact()).isNull();
        assertThat(self.similar()).isEmpty();
        assertThat(indexer.status().pending()).isZero();
        assertThat(dedup.calibration(10).sampled()).isEqualTo(1);
    }

    @Test void editedQuestionIsReEmbedded() {
        Long java = topics.findBySlug("java").orElseThrow().getId();
        questions.update(existing, new QuestionRequest("Explain Kafka consumer groups", null, java, List.of(), null, null, null, null, null, List.of()));
        assertThat(indexer.status().pending()).isZero(); // count-based; fingerprint mismatch is picked up by sync
        indexer.syncNow();
        var r = dedup.check(new DedupController.CheckRequest("Explain internal working of Java HashMap", null));
        assertThat(r.similar()).isEmpty();
    }

    @Test void providerFailureFallsBackToExactOnlyWithNote() {
        when(embeddings.embed(anyList())).thenThrow(new IllegalStateException("boom"));
        var job = imports.start(new ImportService.Upload("f.csv", "question\nExplain internal working of Java HashMap\n".getBytes(StandardCharsets.UTF_8)),
                AttributionMode.NONE, null, null, List.of(), false);
        assertThat(job.parseNotes()).anyMatch(n -> n.contains("Semantic duplicate check failed"));
        assertThat(imports.candidates(job.id(), null).get(0).matchType()).isEqualTo(MatchType.NEW_CANDIDATE);
    }
}
