package com.prephub.question;

import com.prephub.common.DuplicateQuestionException;
import com.prephub.common.Enums.*;
import com.prephub.company.CompanyRepository;
import com.prephub.company.CompanyService;
import com.prephub.importer.ImportDtos.ResolveRequest;
import com.prephub.importer.ImportService;
import com.prephub.question.QuestionDtos.*;
import com.prephub.topic.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class QuestionAndImportIntegrationTest {
    @Autowired QuestionService questions;
    @Autowired QuestionRepository questionRepo;
    @Autowired CompanyService companies;
    @Autowired CompanyRepository companyRepo;
    @Autowired TopicRepository topics;
    @Autowired ImportService imports;
    @Autowired JdbcTemplate jdbc;

    Long java;

    @BeforeEach void clean() {
        for (String t : List.of("import_candidates", "import_jobs", "question_tags", "question_companies", "questions", "tags", "companies"))
            jdbc.update("DELETE FROM " + t);
        java = topics.findBySlug("java").orElseThrow().getId();
    }

    private QuestionRequest req(String text, List<String> tags, List<CompanyAttributionRequest> cos) {
        return new QuestionRequest(text, null, java, tags, Difficulty.INTERMEDIATE, QuestionType.CONCEPTUAL, null, null, null, cos);
    }
    private static CompanyAttributionRequest co(String name) {
        return new CompanyAttributionRequest(null, name, null, null, null, null, null, null, null);
    }
    private ImportService.Upload file(String csv) { return new ImportService.Upload("q.csv", csv.getBytes(StandardCharsets.UTF_8)); }

    @Test void manualQuestionWithTagsAndMultipleCompaniesStaysSingle() {
        var q = questions.create(req("Explain HashMap internals", List.of("HashMap", "Collections", " hashmap "), List.of(co("Amazon"), co(" amazon "), co("Microsoft"))));
        assertThat(q.tags()).extracting(TagRef::name).containsExactly("HashMap", "Collections");
        assertThat(q.companies()).extracting(CompanyAttributionDto::companyName).containsExactlyInAnyOrder("Amazon", "Microsoft");
        assertThat(questionRepo.count()).isEqualTo(1);
        assertThat(companyRepo.count()).isEqualTo(2);
    }

    @Test void exactDuplicateRejectedIgnoringCaseAndWhitespace() {
        var first = questions.create(req("What is a Spring Bean?", List.of(), List.of()));
        assertThatThrownBy(() -> questions.create(req("  what IS a   spring bean ", List.of(), List.of())))
                .isInstanceOfSatisfying(DuplicateQuestionException.class, e -> assertThat(e.getMatchingQuestionId()).isEqualTo(first.id()));
    }

    @Test void concurrentInsertsOfSameQuestionCreateOne() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> fs = new ArrayList<>();
        for (int i = 0; i < 4; i++) fs.add(pool.submit(() -> {
            go.await();
            try { questions.create(req("Explain volatile", List.of("Concurrency"), List.of())); return "ok"; }
            catch (DuplicateQuestionException e) { return "dup"; }
        }));
        go.countDown();
        List<String> out = new ArrayList<>();
        for (var f : fs) out.add(f.get(20, TimeUnit.SECONDS));
        pool.shutdown();
        assertThat(out).containsOnly("ok", "dup");
        assertThat(out.stream().filter("ok"::equals).count()).isEqualTo(1);
        assertThat(questionRepo.count()).isEqualTo(1);
    }

    @Test void filterByCompanyAndTags() {
        questions.create(req("Q one", List.of("JVM"), List.of(co("Amazon"))));
        questions.create(req("Q two", List.of("JVM", "GC"), List.of(co("Google"))));
        Long amazon = companyRepo.findByNormalizedName("amazon").orElseThrow().getId();
        var byCompany = questions.search(new QuestionSpecifications.Filter(null, null, amazon, null, false, null, null, null, null), 0, 20, null);
        assertThat(byCompany.content()).extracting(QuestionDto::questionText).containsExactly("Q one");
        Long jvm = questions.search(new QuestionSpecifications.Filter("q two", null, null, null, false, null, null, null, null), 0, 20, null)
                .content().get(0).tags().get(0).id();
        var byTag = questions.search(new QuestionSpecifications.Filter(null, null, null, List.of(jvm), false, null, null, null, null), 0, 20, null);
        assertThat(byTag.totalElements()).isEqualTo(2);
    }

    @Test void csvWithDefaultCompany() {
        Long amazon = companies.create(new CompanyService.CompanyRequest("Amazon", null, null, null)).id();
        var job = imports.start(file("question,topic\nExplain HashMap internals,Java\nWhat is JIT?,Java\n"),
                AttributionMode.SINGLE_COMPANY, amazon, null, List.of(), false);
        assertThat(job.report().pendingApproval()).isEqualTo(2);
        imports.approve(job.id(), List.of());
        Long amazonCount = questions.search(new QuestionSpecifications.Filter(null, null, amazon, null, false, null, null, null, null), 0, 20, null).totalElements();
        assertThat(amazonCount).isEqualTo(2);
    }

    @Test void csvPerRowMultipleCompaniesAndNoAttribution() {
        var job = imports.start(file("question,company\nA?,Amazon; Microsoft; Google\nB?,Infosys\nC?,\n"), AttributionMode.PER_ROW, null, null, List.of(), false);
        assertThat(job.report().withoutCompany()).isEqualTo(1);
        assertThat(job.report().companiesNotInDirectory()).containsExactly("Amazon", "Microsoft", "Google", "Infosys");
        imports.approve(job.id(), List.of());
        assertThat(companyRepo.count()).isEqualTo(4);
        assertThat(questionRepo.count()).isEqualTo(3);

        var none = imports.start(file("question,company\nD?,Amazon\n"), AttributionMode.NONE, null, null, List.of(), false);
        imports.approve(none.id(), List.of());
        var d = questionRepo.findAll().stream().filter(q -> q.getQuestionText().equals("D?")).findFirst().orElseThrow();
        assertThat(questions.get(d.getId()).companies()).isEmpty();
    }

    @Test void reimportingSameFileCreatesNoDuplicates() {
        String csv = "question,company,answer\nExplain Kafka consumer groups,Accenture,Some answer\n";
        var j1 = imports.start(file(csv), AttributionMode.PER_ROW, null, null, List.of(), false);
        imports.approve(j1.id(), List.of());
        var j2 = imports.start(file(csv), AttributionMode.PER_ROW, null, null, List.of(), false);
        assertThat(j2.previousImportsOfSameFile()).containsExactly(j1.id());
        assertThat(j2.report().exactDuplicatesSkipped()).isEqualTo(1);
        imports.approve(j2.id(), List.of());
        assertThat(questionRepo.count()).isEqualTo(1);
    }

    @Test void duplicateWithNewCompanyIsReviewedAndMergedPreservingAnswer() {
        var orig = questions.create(new QuestionRequest("Explain HashMap internals", "Original answer", java, List.of("HashMap"),
                null, null, null, null, null, List.of(co("Amazon"))));
        var job = imports.start(file("question,company,answer,tags\nexplain hashmap INTERNALS?,Google,Different answer,Collections\n"),
                AttributionMode.PER_ROW, null, null, List.of(), false);
        var cand = imports.candidates(job.id(), ReviewStatus.NEEDS_REVIEW);
        assertThat(cand).hasSize(1);
        assertThat(cand.get(0).matchedQuestionId()).isEqualTo(orig.id());
        assertThatThrownBy(() -> imports.resolve(job.id(), cand.get(0).id(), new ResolveRequest(ReviewAction.KEEP_SEPARATE, null)))
                .isInstanceOf(com.prephub.common.BadRequestException.class);
        imports.resolve(job.id(), cand.get(0).id(), new ResolveRequest(ReviewAction.MERGE, null));
        var merged = questions.get(orig.id());
        assertThat(questionRepo.count()).isEqualTo(1);
        assertThat(merged.answerText()).isEqualTo("Original answer");
        assertThat(merged.notes()).contains("Different answer");
        assertThat(merged.companies()).extracting(CompanyAttributionDto::companyName).containsExactlyInAnyOrder("Amazon", "Google");
        assertThat(merged.tags()).extracting(TagRef::name).containsExactlyInAnyOrder("HashMap", "Collections");
        assertThat(imports.get(job.id()).status()).isEqualTo(ImportStatus.COMPLETED);
    }

    @Test void inFileDuplicatesMergeCompaniesIntoOneQuestion() {
        var job = imports.start(file("question,company\nWhat is IoC?,TCS\nwhat is ioc,Infosys\n"), AttributionMode.PER_ROW, null, null, List.of(), false);
        imports.approve(job.id(), List.of());
        assertThat(questionRepo.count()).isEqualTo(1);
        var q = questions.get(questionRepo.findAll().get(0).getId());
        assertThat(q.companies()).extracting(CompanyAttributionDto::companyName).containsExactlyInAnyOrder("TCS", "Infosys");
    }

    @Test void partiallyInvalidImport() {
        var job = imports.start(file("question,difficulty\nGood one,ADVANCED\n,BEGINNER\nBad diff,EXPERT\n"), AttributionMode.NONE, null, null, List.of(), false);
        assertThat(job.report().invalid()).isEqualTo(2);
        assertThat(job.report().pendingApproval()).isEqualTo(1);
        imports.approve(job.id(), List.of());
        assertThat(questionRepo.count()).isEqualTo(1);
        String failed = new String(imports.failedRowsCsv(job.id()), StandardCharsets.UTF_8);
        assertThat(failed).contains("Bad diff").contains("Unknown difficulty").contains("Question text is empty");
        assertThat(imports.get(job.id()).status()).isEqualTo(ImportStatus.COMPLETED);
    }
}
