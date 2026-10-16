package com.prephub.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prephub.ai.AiDtos.*;
import com.prephub.ai.FollowUpValidator.*;
import com.prephub.common.BadRequestException;
import com.prephub.common.Enums.*;
import com.prephub.common.NotFoundException;
import com.prephub.common.TextNormalizer;
import com.prephub.company.CompanyService;
import com.prephub.importer.SemanticDuplicateDetector;
import com.prephub.question.Question;
import com.prephub.question.QuestionRepository;
import com.prephub.question.QuestionSpecifications;
import com.prephub.question.QuestionWriter;
import com.prephub.tag.Tag;
import com.prephub.tag.TagRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/**
 * AI follow-ups and answer drafts. Database work happens in short transactions; the (slow) model call happens
 * outside any transaction. Every AI question goes through the same pipeline as imports: schema validation,
 * normalisation, exact fingerprint check, semantic check, tag validation, and it is stored only as PENDING review.
 * AI questions never inherit company attribution from the parent question.
 */
@Service
public class AiQuestionService {
    public static final String SUGGESTED_TAGS_PREFIX = "Suggested new tags (not applied): ";

    private final AiTextClient ai;
    private final QuestionRepository questions;
    private final TagRepository tags;
    private final CompanyService companies;
    private final QuestionWriter writer;
    private final SemanticDuplicateDetector semantic;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public AiQuestionService(AiTextClient ai, QuestionRepository questions, TagRepository tags, CompanyService companies,
                             QuestionWriter writer, SemanticDuplicateDetector semantic, JdbcTemplate jdbc,
                             TransactionTemplate tx, ObjectMapper json) {
        this.ai = ai; this.questions = questions; this.tags = tags; this.companies = companies; this.writer = writer;
        this.semantic = semantic; this.jdbc = jdbc; this.tx = tx;
        this.json = json.copy().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private record Base(Long id, String text, String fingerprint, Long topicId, String topic, List<String> tags,
                        Difficulty difficulty, List<String> related) {}

    private Base loadBase(Long id) {
        return tx.execute(s -> {
            Question q = questions.findById(id).orElseThrow(() -> new NotFoundException("Question", id));
            List<String> tagNames = q.getTags().stream().map(Tag::getName).toList();
            List<String> related = new ArrayList<>(jdbc.queryForList(
                    "SELECT q.question_text FROM question_relations r JOIN questions q ON q.id = r.child_question_id " +
                    "WHERE r.parent_question_id = ? ORDER BY r.sequence_number", String.class, id));
            List<Long> tagIds = q.getTags().stream().map(Tag::getId).toList();
            if (!tagIds.isEmpty()) {
                var spec = QuestionSpecifications.of(new QuestionSpecifications.Filter(null, q.getTopic().getId(), null, tagIds,
                        false, null, null, null, ApprovalStatus.APPROVED));
                questions.findAll(spec, PageRequest.of(0, 11)).forEach(o -> { if (!o.getId().equals(id)) related.add(o.getQuestionText()); });
            }
            return new Base(q.getId(), q.getQuestionText(), q.getFingerprint(), q.getTopic().getId(), q.getTopic().getName(),
                    tagNames, q.getDifficulty(), related);
        });
    }

    // ------------------------------------------------------------------ follow-ups

    public FollowUpBatch generateFollowUps(Long questionId, GenerateFollowUpsRequest req) {
        if (!ai.available()) throw new AiExceptions.AiUnavailableException(ai.status());
        int count = req.count() == null ? 5 : req.count();
        FollowUpMode mode = req.mode() == null ? FollowUpMode.SESSION_ONLY : req.mode();
        Base base = loadBase(questionId);
        String company = req.companyId() == null ? null : tx.execute(s -> companies.get(req.companyId()).getName());

        var ctx = new PromptBuilder.FollowUpContext(base.id(), base.text(), base.topic(), base.tags(),
                base.difficulty() == null ? null : base.difficulty().name(), company, req.role(), req.interviewRound(),
                base.related(), req.sessionQuestions(), req.previousAnswer(), count);
        RawBatch raw = callForBatch(PromptBuilder.followUpSystem(), PromptBuilder.followUpUser(ctx));

        List<String> excluded = new ArrayList<>();
        excluded.add(base.text());
        if (req.sessionQuestions() != null) excluded.addAll(req.sessionQuestions());
        Outcome outcome = FollowUpValidator.validate(raw, base.difficulty(), excluded, count);
        if (outcome.valid().isEmpty())
            throw new AiExceptions.AiOutputException("The AI reply contained no valid follow-up questions", outcome.rejected());

        List<FollowUpItem> items = tx.execute(s -> outcome.valid().stream().map(v -> toItem(base.id(), v, mode)).toList());
        String disclaimer = "AI-generated practice questions" + (company != null
                ? ". The style is adapted to " + company + ", but these are NOT questions reported as asked at " + company + "." : ".");
        return new FollowUpBatch(base.id(), ai.provider() + "/" + ai.model(), company, disclaimer, items, outcome.rejected());
    }

    private RawBatch callForBatch(String system, String user) {
        String reply = ai.complete(system, user);
        try {
            return parse(reply);
        } catch (IllegalArgumentException first) {
            String retry = ai.complete(system, user + "\nYour previous reply was not valid JSON for the required shape. Reply with the JSON object only.");
            try { return parse(retry); }
            catch (IllegalArgumentException second) {
                throw new AiExceptions.AiOutputException("The AI reply was not valid JSON for the follow-up schema", List.of(second.getMessage()));
            }
        }
    }

    RawBatch parse(String reply) {
        try { return json.readValue(FollowUpValidator.extractJsonObject(reply), RawBatch.class); }
        catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("JSON does not match the schema (" + e.getClass().getSimpleName() + ")"); }
    }

    private FollowUpItem toItem(Long parentId, ValidFollowUp v, FollowUpMode mode) {
        List<String> existing = new ArrayList<>(), suggested = new ArrayList<>();
        splitTags(v.tags(), existing, suggested);
        DuplicateInfo dup = duplicateOf(v.question(), v.fingerprint());
        Persistence p = mode == FollowUpMode.SESSION_ONLY ? Persistence.SESSION_ONLY
                : dup != null && "EXACT_DUPLICATE".equals(dup.matchType()) ? Persistence.LINK_EXISTING : Persistence.REVIEW_ELIGIBLE;
        return new FollowUpItem(parentId, v.question(), v.expectedAnswerPoints(), v.difficulty(), existing, suggested, v.reason(), p, dup);
    }

    private DuplicateInfo duplicateOf(String text, String fingerprint) {
        var exact = questions.findByFingerprint(fingerprint);
        if (exact.isPresent())
            return new DuplicateInfo("EXACT_DUPLICATE", exact.get().getId(), exact.get().getQuestionText(), exact.get().getApprovalStatus(), 1.0);
        var sims = semantic.findSimilar(text, 1);
        if (!sims.isEmpty()) {
            var m = sims.get(0);
            return questions.findById(m.questionId()).map(q -> new DuplicateInfo(m.score() >= 0.92 ? "LIKELY_SEMANTIC_DUPLICATE" : "POSSIBLE_DUPLICATE",
                    q.getId(), q.getQuestionText(), q.getApprovalStatus(), m.score())).orElse(null);
        }
        return null;
    }

    /** AI tags are only applied when the tag already exists; new ones are kept as suggestions for the reviewer. */
    private void splitTags(List<String> names, List<String> existing, List<String> suggested) {
        for (String n : names) {
            Optional<Tag> t = tags.findByNormalizedName(TextNormalizer.normalizeName(n));
            if (t.isPresent()) existing.add(t.get().getName()); else suggested.add(n);
        }
    }

    /** Saves a reviewed follow-up candidate as a PENDING question linked to its parent (or links an existing question). */
    public SaveFollowUpResult saveForReview(Long parentId, SaveFollowUpRequest r) {
        Base base = loadBase(parentId);
        ValidFollowUp v;
        try {
            v = FollowUpValidator.validateOne(new RawFollowUp(r.question(), r.expectedAnswerPoints(), r.difficulty(), r.tags(), r.reason()), base.difficulty());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid follow-up: " + e.getMessage());
        }
        if (v.fingerprint().equals(base.fingerprint())) throw new BadRequestException("The follow-up is identical to its parent question");

        var existing = questions.findByFingerprint(v.fingerprint());
        if (existing.isPresent()) return linkExisting(parentId, existing.get());

        List<String> applied = new ArrayList<>(), suggested = new ArrayList<>();
        tx.executeWithoutResult(s -> splitTags(v.tags(), applied, suggested));
        String notes = "AI follow-up of question #" + parentId + ".\nWhy asked: " + v.reason()
                + (suggested.isEmpty() ? "" : "\n" + SUGGESTED_TAGS_PREFIX + String.join(", ", suggested));
        String answer = "Expected answer points (AI-generated, unverified):\n- " + String.join("\n- ", v.expectedAnswerPoints());
        try {
            var res = writer.insert(new QuestionWriter.NewQuestion(v.question(), answer, base.topicId(), applied, v.difficulty(), null,
                    AnswerStatus.UNVERIFIED, SourceType.AI_GENERATED, "AI follow-up of #" + parentId + " (" + ai.provider() + "/" + ai.model() + ")",
                    notes, ApprovalStatus.PENDING, List.of()));
            link(parentId, res.question().getId());
            return new SaveFollowUpResult(SaveStatus.CREATED_PENDING_REVIEW, res.question().getId(), ApprovalStatus.PENDING,
                    "Saved for review. It will appear in the question bank after approval.");
        } catch (DataIntegrityViolationException race) {
            Question q = questions.findByFingerprint(v.fingerprint()).orElseThrow(() -> race);
            return linkExisting(parentId, q);
        }
    }

    private SaveFollowUpResult linkExisting(Long parentId, Question q) {
        if (q.getId().equals(parentId)) throw new BadRequestException("The follow-up is identical to its parent question");
        link(parentId, q.getId());
        return new SaveFollowUpResult(SaveStatus.LINKED_EXISTING, q.getId(), q.getApprovalStatus(),
                "An identical question already exists (#" + q.getId() + "); it was linked as a follow-up instead of creating a copy.");
    }

    void link(Long parentId, Long childId) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM question_relations WHERE parent_question_id = ? AND child_question_id = ?",
                Integer.class, parentId, childId);
        if (exists != null && exists > 0) return;
        Integer seq = jdbc.queryForObject("SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM question_relations WHERE parent_question_id = ?",
                Integer.class, parentId);
        try {
            jdbc.update("INSERT INTO question_relations (parent_question_id, child_question_id, relation_type, sequence_number) VALUES (?, ?, 'FOLLOW_UP', ?)",
                    parentId, childId, seq);
        } catch (DuplicateKeyException ignored) {
            // concurrent link of the same pair: already present
        }
    }

    public List<LinkedFollowUp> followUps(Long parentId) {
        if (!questions.existsById(parentId)) throw new NotFoundException("Question", parentId);
        return jdbc.query("SELECT q.id, q.question_text, q.approval_status, q.difficulty, r.sequence_number FROM question_relations r " +
                        "JOIN questions q ON q.id = r.child_question_id WHERE r.parent_question_id = ? ORDER BY r.sequence_number",
                (rs, i) -> new LinkedFollowUp(rs.getLong(1), rs.getString(2), ApprovalStatus.valueOf(rs.getString(3)), rs.getString(4),
                        rs.getObject(5) == null ? null : rs.getInt(5)),
                parentId);
    }

    // ------------------------------------------------------------------ answers

    public DraftAnswer draftAnswer(Long questionId) {
        if (!ai.available()) throw new AiExceptions.AiUnavailableException(ai.status());
        Base b = loadBase(questionId);
        String reply = ai.complete(PromptBuilder.answerSystem(),
                PromptBuilder.answerUser(b.text(), b.topic(), b.tags(), b.difficulty() == null ? null : b.difficulty().name()));
        String draft = reply == null ? null : reply.trim();
        if (draft == null || draft.length() < 20) throw new AiExceptions.AiOutputException("The AI returned an empty answer", List.of("reply too short"));
        if (draft.length() > 20000) draft = draft.substring(0, 20000);
        return new DraftAnswer(questionId, ai.provider() + "/" + ai.model(), draft,
                "AI draft, not saved. Verify it, then paste it into the question's answer and mark it verified if correct.");
    }
}
