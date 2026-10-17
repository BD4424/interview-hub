package com.prephub.dashboard;

import com.prephub.common.Enums.ApprovalStatus;
import com.prephub.common.Enums.ReviewStatus;
import com.prephub.importer.ImportCandidateRepository;
import com.prephub.importer.ImportJobRepository;
import com.prephub.practice.PracticeDtos;
import com.prephub.practice.ProgressService;
import com.prephub.question.Question;
import com.prephub.question.QuestionRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final QuestionRepository questions;
    private final ImportJobRepository jobs;
    private final ImportCandidateRepository candidates;
    private final ProgressService progress;

    public DashboardController(QuestionRepository q, ImportJobRepository j, ImportCandidateRepository c, ProgressService p) {
        questions = q; jobs = j; candidates = c; progress = p;
    }

    public record Bucket(String label, long count) {}
    public record RecentImport(Long id, String filename, String status, int total, int imported, Instant createdAt) {}
    public record Dashboard(long totalApproved, long answered, long unanswered, List<Bucket> byTopic, List<Bucket> byCompany,
                            long pendingDuplicateReviews, long pendingAiReviews, List<RecentImport> recentImports,
                            PracticeDtos.ProgressSummary practice, List<PracticeDtos.TopicProgress> weakTopics,
                            List<PracticeDtos.SessionSummary> recentSessions, List<PracticeDtos.DayProgress> last14Days) {}

    @GetMapping @Transactional(readOnly = true)
    public Dashboard get() {
        long total = questions.countApproved(), answered = questions.countAnswered();
        Specification<Question> pending = (r, q, cb) -> cb.equal(r.get("approvalStatus"), ApprovalStatus.PENDING);
        PracticeDtos.Progress p = progress.progress();
        List<PracticeDtos.DayProgress> days = p.last30Days();
        return new Dashboard(total, answered, total - answered,
                questions.countByTopic().stream().map(x -> new Bucket(x.getLabel(), x.getTotal())).toList(),
                questions.countByCompany().stream().map(x -> new Bucket(x.getLabel(), x.getTotal())).toList(),
                candidates.countByReviewStatus(ReviewStatus.NEEDS_REVIEW),
                questions.count(pending),
                jobs.findTop5ByOrderByCreatedAtDesc().stream().map(j -> new RecentImport(j.getId(), j.getFilename(), j.getStatus().name(),
                        j.getTotalRecords(), j.getImportedCount(), j.getCreatedAt())).toList(),
                p.summary(), p.weakTopics(), p.recentSessions().stream().limit(5).toList(),
                days.subList(Math.max(0, days.size() - 14), days.size()));
    }
}
