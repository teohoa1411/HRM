package com.hrms.domain.timesheet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Khóa timesheet theo tháng ở cấp độ công ty.
 * Khi locked = true, nhân sự không thể chỉnh sửa timesheet tháng đó.
 * Quản lý (COMPANY_ADMIN) vẫn được phép chỉnh sửa dù đã khóa.
 */
@Entity
@Table(name = "timesheet_period_locks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "year_month"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TimesheetPeriodLock {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Format: YYYY-MM, ví dụ: 2025-04 */
    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "locked", nullable = false)
    @Builder.Default
    private boolean locked = false;

    /** userId của người khóa */
    @Column(name = "locked_by")
    private Long lockedBy;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    /** MANUAL | PAYROLL_CONFIRMED | PAYROLL_EXPORTED | STATEMENT_SENT */
    @Column(name = "lock_reason", length = 30)
    private String lockReason;

    @Column(name = "payroll_lock_at")
    private LocalDateTime payrollLockAt;

    @Column(name = "statement_lock_at")
    private LocalDateTime statementLockAt;

    @Column(name = "locked_by_report_id")
    private Long lockedByReportId;

    /** Kế toán đã xác nhận timesheet (bước 2 sau khi HR khóa). Khi true: không ai sửa được. */
    @Column(name = "accountant_confirmed", nullable = false)
    @Builder.Default
    private boolean accountantConfirmed = false;

    /** userId của Kế toán đã xác nhận */
    @Column(name = "accountant_confirmed_by")
    private Long accountantConfirmedBy;

    @Column(name = "accountant_confirmed_at")
    private LocalDateTime accountantConfirmedAt;

    /** Lý do khi Kế toán revert xác nhận (bắt buộc nhập khi hủy). */
    @Column(name = "accountant_revert_reason", length = 500)
    private String accountantRevertReason;

    /** Biên bản đã đóng — khóa hoàn toàn timesheet, chỉ COMPANY_ADMIN mở lại */
    @Column(name = "statement_closed", nullable = false)
    @Builder.Default
    private boolean statementClosed = false;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_reason", length = 500)
    private String closedReason;

    @Column(name = "reopen_reason", length = 500)
    private String reopenReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
