package com.prephub.importer;

import com.prephub.common.Enums.MatchType;
import com.prephub.common.Enums.ReviewStatus;
import jakarta.persistence.*;

import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "import_candidates")
public class ImportCandidate {
    /** Multi-valued proposed fields are stored newline-separated (values never contain newlines after cleaning). */
    public static final String SEP = "\n";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "import_job_id") private ImportJob job;
    @Column(name = "source_reference", nullable = false) private String sourceReference;
    @Column(name = "raw_question") private String rawQuestion;
    private String fingerprint;
    @Column(name = "proposed_answer") private String proposedAnswer;
    @Column(name = "proposed_topic") private String proposedTopic;
    @Column(name = "proposed_tags") private String proposedTags;
    @Column(name = "proposed_companies") private String proposedCompanies;
    @Column(name = "proposed_difficulty") private String proposedDifficulty;
    @Column(name = "proposed_type") private String proposedType;
    @Column(name = "interview_round") private String interviewRound;
    private String role;
    @Column(name = "job_level") private String jobLevel;
    @Enumerated(EnumType.STRING) @Column(name = "match_type", nullable = false) private MatchType matchType;
    @Column(name = "matched_question_id") private Long matchedQuestionId;
    @Column(name = "similarity_score") private Double similarityScore;
    @Enumerated(EnumType.STRING) @Column(name = "review_status", nullable = false) private ReviewStatus reviewStatus;
    @Column(name = "rejection_reason") private String rejectionReason;
    @Column(name = "created_question_id") private Long createdQuestionId;

    public static String join(List<String> v) { return v == null || v.isEmpty() ? null : String.join(SEP, v); }
    public static List<String> split(String v) { return v == null || v.isBlank() ? List.of() : Arrays.stream(v.split(SEP)).filter(s -> !s.isBlank()).toList(); }

    public List<String> tagList() { return split(proposedTags); }
    public List<String> companyList() { return split(proposedCompanies); }

    public Long getId() { return id; }
    public ImportJob getJob() { return job; }
    public void setJob(ImportJob j) { this.job = j; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String s) { this.sourceReference = s; }
    public String getRawQuestion() { return rawQuestion; }
    public void setRawQuestion(String r) { this.rawQuestion = r; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String f) { this.fingerprint = f; }
    public String getProposedAnswer() { return proposedAnswer; }
    public void setProposedAnswer(String a) { this.proposedAnswer = a; }
    public String getProposedTopic() { return proposedTopic; }
    public void setProposedTopic(String t) { this.proposedTopic = t; }
    public String getProposedTags() { return proposedTags; }
    public void setProposedTags(String t) { this.proposedTags = t; }
    public String getProposedCompanies() { return proposedCompanies; }
    public void setProposedCompanies(String c) { this.proposedCompanies = c; }
    public String getProposedDifficulty() { return proposedDifficulty; }
    public void setProposedDifficulty(String d) { this.proposedDifficulty = d; }
    public String getProposedType() { return proposedType; }
    public void setProposedType(String t) { this.proposedType = t; }
    public String getInterviewRound() { return interviewRound; }
    public void setInterviewRound(String r) { this.interviewRound = r; }
    public String getRole() { return role; }
    public void setRole(String r) { this.role = r; }
    public String getJobLevel() { return jobLevel; }
    public void setJobLevel(String j) { this.jobLevel = j; }
    public MatchType getMatchType() { return matchType; }
    public void setMatchType(MatchType m) { this.matchType = m; }
    public Long getMatchedQuestionId() { return matchedQuestionId; }
    public void setMatchedQuestionId(Long id) { this.matchedQuestionId = id; }
    public Double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(Double s) { this.similarityScore = s; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(ReviewStatus s) { this.reviewStatus = s; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String r) { this.rejectionReason = r; }
    public Long getCreatedQuestionId() { return createdQuestionId; }
    public void setCreatedQuestionId(Long id) { this.createdQuestionId = id; }
}
