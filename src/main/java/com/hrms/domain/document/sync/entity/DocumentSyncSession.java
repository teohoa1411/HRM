package com.hrms.domain.document.sync.entity;

import com.hrms.domain.common.enums.SyncTargetType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_sync_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentSyncSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "triggered_by", nullable = false)
    private Long triggeredBy;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "RUNNING";

    @Column(name = "total_documents", nullable = false)
    @Builder.Default
    private int totalDocuments = 0;

    @Column(name = "synced_count", nullable = false)
    @Builder.Default
    private int syncedCount = 0;

    @Column(name = "skipped_count", nullable = false)
    @Builder.Default
    private int skippedCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private int failedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private SyncTargetType targetType;

    @Column(name = "target_base_path", nullable = false, length = 500)
    private String targetBasePath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markCompleted(int total, int synced, int skipped, int failed) {
        this.totalDocuments = total;
        this.syncedCount    = synced;
        this.skippedCount   = skipped;
        this.failedCount    = failed;
        this.completedAt    = LocalDateTime.now();
        this.status         = failed == total && total > 0 ? "FAILED" : "COMPLETED";
    }
}
