package com.prephub.question;

import com.prephub.common.Enums.AttributionStatus;
import com.prephub.company.Company;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "question_companies")
public class QuestionCompany {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "question_id") private Question question;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    private String role;
    @Column(name = "interview_round") private String interviewRound;
    @Column(name = "job_level") private String jobLevel;
    @Column(name = "date_asked") private LocalDate dateAsked;
    @Column(name = "source_reference") private String sourceReference;
    @Enumerated(EnumType.STRING) @Column(name = "attribution_status", nullable = false)
    private AttributionStatus attributionStatus = AttributionStatus.REPORTED;
    private String notes;

    public Long getId() { return id; }
    public Question getQuestion() { return question; }
    public void setQuestion(Question q) { this.question = q; }
    public Company getCompany() { return company; }
    public void setCompany(Company c) { this.company = c; }
    public String getRole() { return role; }
    public void setRole(String r) { this.role = r; }
    public String getInterviewRound() { return interviewRound; }
    public void setInterviewRound(String r) { this.interviewRound = r; }
    public String getJobLevel() { return jobLevel; }
    public void setJobLevel(String j) { this.jobLevel = j; }
    public LocalDate getDateAsked() { return dateAsked; }
    public void setDateAsked(LocalDate d) { this.dateAsked = d; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String s) { this.sourceReference = s; }
    public AttributionStatus getAttributionStatus() { return attributionStatus; }
    public void setAttributionStatus(AttributionStatus a) { this.attributionStatus = a; }
    public String getNotes() { return notes; }
    public void setNotes(String n) { this.notes = n; }
}
