package com.hrms.domain.internal_statement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "internal_statement_entries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InternalStatementEntry {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "record_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** Tổng giờ nhân sự log trong ngày (từ timesheet, tổng hợp tất cả project) */
    @Column(name = "raw_hours", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    private BigDecimal rawHours = BigDecimal.ZERO;

    /** Giờ công HR điều chỉnh (mặc định = rawHours + giờ phép nếu có) */
    @Column(name = "adjusted_hours", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    private BigDecimal adjustedHours = BigDecimal.ZERO;

    @Column(name = "is_leave_day", nullable = false)
    @Builder.Default
    private boolean leaveDay = false;

    @Column(name = "is_half_leave", nullable = false)
    @Builder.Default
    private boolean halfLeave = false;

    @Column(name = "is_paid_leave", nullable = false)
    @Builder.Default
    private boolean paidLeave = false;

    @Column(name = "leave_type_name", length = 100)
    private String leaveTypeName;

    /** Ghi chú của HR (không phải timesheet note) */
    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "updated_by")
    private Long updatedBy;

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
