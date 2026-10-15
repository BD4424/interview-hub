package com.prephub.company;

import jakarta.persistence.*;

@Entity
@Table(name = "companies")
public class Company {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    @Column(name = "normalized_name", nullable = false, unique = true) private String normalizedName;
    private String website;
    private String industry;
    private String notes;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String n) { this.normalizedName = n; }
    public String getWebsite() { return website; }
    public void setWebsite(String w) { this.website = w; }
    public String getIndustry() { return industry; }
    public void setIndustry(String i) { this.industry = i; }
    public String getNotes() { return notes; }
    public void setNotes(String n) { this.notes = n; }
}
