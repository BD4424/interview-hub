package com.prephub.importer;

import com.prephub.common.Enums.AttributionMode;
import com.prephub.common.Enums.ImportStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "import_jobs")
public class ImportJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String filename;
    @Column(name = "file_hash", nullable = false, length = 64) private String fileHash;
    @Column(nullable = false) private String format;
    @Enumerated(EnumType.STRING) @Column(name = "attribution_mode", nullable = false) private AttributionMode attributionMode;
    @Column(name = "default_company_id") private Long defaultCompanyId;
    @Column(name = "default_topic_id") private Long defaultTopicId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ImportStatus status;
    @Column(name = "total_records", nullable = false) private int totalRecords;
    @Column(name = "imported_count", nullable = false) private int importedCount;
    @Column(name = "duplicate_count", nullable = false) private int duplicateCount;
    @Column(name = "failed_count", nullable = false) private int failedCount;
    @Column(name = "error_message") private String errorMessage;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public void setFilename(String f) { this.filename = f; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String h) { this.fileHash = h; }
    public String getFormat() { return format; }
    public void setFormat(String f) { this.format = f; }
    public AttributionMode getAttributionMode() { return attributionMode; }
    public void setAttributionMode(AttributionMode m) { this.attributionMode = m; }
    public Long getDefaultCompanyId() { return defaultCompanyId; }
    public void setDefaultCompanyId(Long id) { this.defaultCompanyId = id; }
    public Long getDefaultTopicId() { return defaultTopicId; }
    public void setDefaultTopicId(Long id) { this.defaultTopicId = id; }
    public ImportStatus getStatus() { return status; }
    public void setStatus(ImportStatus s) { this.status = s; }
    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int t) { this.totalRecords = t; }
    public int getImportedCount() { return importedCount; }
    public void setImportedCount(int c) { this.importedCount = c; }
    public int getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(int c) { this.duplicateCount = c; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int c) { this.failedCount = c; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String e) { this.errorMessage = e; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant c) { this.completedAt = c; }
}
