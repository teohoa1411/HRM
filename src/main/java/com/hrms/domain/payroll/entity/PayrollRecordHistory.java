package com.hrms.domain.payroll.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_record_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollRecordHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payroll_record_id", nullable = false)
    private Long payrollRecordId;

    @Column(name = "snapshot_data", nullable = false, columnDefinition = "JSON")
    private String snapshotData;

    /** RESET_BY_ADMIN | TIMESHEET_MODIFIED | MANUAL_ADJUST */
    @Column(name = "change_type", nullable = false, length = 30)
    private String changeType;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "old_file_s3_key", length = 500)
    private String oldFileS3Key;

    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @PrePersist
    protected void onCreate() { changedAt = LocalDateTime.now(); }
}
