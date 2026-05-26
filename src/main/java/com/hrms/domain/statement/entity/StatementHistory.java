package com.hrms.domain.statement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Audit trail cho mọi thay đổi trạng thái của Statement.
 *
 * <p>Mỗi khi statement chuyển trạng thái (DRAFT → SENT, SENT → CONFIRMED, v.v.),
 * một record mới được tạo để lưu lại ai thay đổi, khi nào, và lý do gì.</p>
 *
 * <p>Snapshot JSON tùy chọn: lưu toàn bộ nội dung statement tại thời điểm thay đổi
 * để có thể khôi phục hoặc so sánh lịch sử.</p>
 */
@Entity
@Table(name = "statement_history",
        indexes = {
                @Index(name = "idx_sh_statement", columnList = "statement_id"),
                @Index(name = "idx_sh_changed_at", columnList = "changed_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → statements.id */
    @Column(name = "statement_id", nullable = false)
    private Long statementId;

    /**
     * Trạng thái trước khi thay đổi.
     * NULL nghĩa là statement vừa được tạo mới.
     */
    @Column(name = "from_status", length = 15)
    private String fromStatus;

    /** Trạng thái sau khi thay đổi: DRAFT|SENT|CONFIRMED|STALE|EXPORTED */
    @Column(name = "to_status", nullable = false, length = 15)
    private String toStatus;

    /** User ID thực hiện thay đổi */
    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /** Lý do thay đổi / ghi chú kế toán */
    @Column(name = "change_reason", length = 500)
    private String changeReason;

    /**
     * Snapshot JSON toàn bộ nội dung statement tại thời điểm thay đổi.
     * Dùng để audit: so sánh trước/sau, phát hiện bất thường.
     * Có thể null nếu không cần lưu snapshot (ví dụ DRAFT → DRAFT correction).
     */
    @Column(name = "snapshot_json", columnDefinition = "MEDIUMTEXT")
    private String snapshotJson;

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
    }

    // ── factory methods ───────────────────────────────────────────────────────

    /**
     * Tạo history record khi statement được tạo mới (from = null).
     */
    public static StatementHistory created(Long statementId, Long createdBy) {
        return StatementHistory.builder()
                .statementId(statementId)
                .fromStatus(null)
                .toStatus("DRAFT")
                .changedBy(createdBy)
                .changedAt(LocalDateTime.now())
                .changeReason("Statement created")
                .build();
    }

    /**
     * Tạo history record khi statement chuyển trạng thái.
     */
    public static StatementHistory transition(Long statementId,
                                               String fromStatus,
                                               String toStatus,
                                               Long changedBy,
                                               String reason) {
        return StatementHistory.builder()
                .statementId(statementId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedBy)
                .changedAt(LocalDateTime.now())
                .changeReason(reason)
                .build();
    }

    /**
     * Tạo history record khi timesheet bị thay đổi sau khi export → STALE.
     */
    public static StatementHistory markedStale(Long statementId,
                                                String previousStatus,
                                                Long systemUserId,
                                                String staleReason) {
        return StatementHistory.builder()
                .statementId(statementId)
                .fromStatus(previousStatus)
                .toStatus("STALE")
                .changedBy(systemUserId)
                .changedAt(LocalDateTime.now())
                .changeReason("Timesheet changed after export: " + staleReason)
                .build();
    }
}
