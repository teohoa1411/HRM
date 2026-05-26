package com.hrms.domain.leave.entity;

import com.hrms.domain.common.entity.BaseCompanyEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class LeaveRequest extends BaseCompanyEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal totalDays;

    @Column(name = "is_half_day", nullable = false)
    @Builder.Default
    private boolean halfDay = false;

    /**
     * Chỉ có giá trị khi halfDay = true.
     * MORNING = nghỉ nửa buổi sáng (4h sáng)
     * AFTERNOON = nghỉ nửa buổi chiều (4h chiều)
     * null = nghỉ cả ngày
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "half_day_period", length = 20)
    private HalfDayPeriod halfDayPeriod;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    public enum LeaveStatus { PENDING, APPROVED, REJECTED, CANCELLED }

    public enum HalfDayPeriod { MORNING, AFTERNOON }
}
