package com.hrms.domain.statement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "statement_line_item_daily")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StatementLineItemDaily {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "statement_line_item_id", nullable = false)
    private Long statementLineItemId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** Giờ từ biên bản nội bộ (tham chiếu) */
    @Column(name = "internal_hours", precision = 4, scale = 1)
    @Builder.Default
    private BigDecimal internalHours = BigDecimal.ZERO;

    /** Giờ gửi cho khách hàng (accountant có thể chỉnh) */
    @Column(name = "customer_hours", precision = 4, scale = 1)
    @Builder.Default
    private BigDecimal customerHours = BigDecimal.ZERO;

    /** Note gốc từ timesheet */
    @Column(name = "timesheet_note", length = 500)
    private String timesheetNote;

    /** Note của accountant */
    @Column(name = "accountant_note", length = 500)
    private String accountantNote;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
