package com.prephub.importer;

import com.prephub.common.Enums.AttributionMode;
import com.prephub.common.Enums.ImportStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "import_jobs")
@Getter
@Setter
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
    @Column(name = "parse_notes") private String parseNotes;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }

}
