package com.hrms.domain.internal_statement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "internal_statement_summaries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InternalStatementSummary {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "record_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "total_raw_hours", precision = 7, scale = 1)
    @Builder.Default
    private BigDecimal totalRawHours = BigDecimal.ZERO;

    @Column(name = "total_adjusted_hours", precision = 7, scale = 1)
    @Builder.Default
    private BigDecimal totalAdjustedHours = BigDecimal.ZERO;

    /** totalAdjustedHours / 8.0 */
    @Column(name = "total_adjusted_days", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal totalAdjustedDays = BigDecimal.ZERO;

    /** Số ngày nghỉ phép cả ngày */
    @Column(name = "total_leave_days")
    @Builder.Default
    private int totalLeaveDays = 0;

    /** Số ngày nghỉ phép nửa ngày */
    @Column(name = "total_half_leave_days")
    @Builder.Default
    private int totalHalfLeaveDays = 0;

    /** DRAFT | CONFIRMED */
    @Column(name = "status", length = 15, nullable = false)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
