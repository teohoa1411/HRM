package com.hrms.domain.leave.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Quota nghỉ phép theo năm của từng nhân sự.
 *
 * <p>Mỗi record đại diện cho số ngày phép được cấp (total_quota),
 * số ngày đã dùng (used_days), và các thông tin liên quan cho một loại phép
 * trong một năm cụ thể.</p>
 *
 * <p>Khi LeaveRequest được APPROVED, used_days tự động tăng lên.
 * Khi LeaveRequest bị REJECTED hoặc CANCELLED, used_days giảm về.</p>
 */
@Entity
@Table(name = "leave_balances",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"company_id", "employee_id", "leave_type_id", "year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    /** Năm áp dụng, ví dụ: 2025 */
    @Column(name = "year", nullable = false)
    private int year;

    /** Tổng ngày phép được cấp trong năm (cấu hình riêng theo nhân sự hoặc theo loại phép) */
    @Column(name = "total_quota", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal totalQuota = BigDecimal.ZERO;

    /**
     * Số ngày đã sử dụng (tổng từ leave_requests APPROVED).
     * Được cập nhật tự động khi duyệt / huỷ đơn phép.
     */
    @Column(name = "used_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal usedDays = BigDecimal.ZERO;

    /** Ngày phép chuyển từ năm trước (carry over) */
    @Column(name = "carried_over", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal carriedOver = BigDecimal.ZERO;

    /** Ngày phép hết hạn chưa sử dụng (cuối năm) */
    @Column(name = "expired_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal expiredDays = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── business helpers ──────────────────────────────────────────────────────

    /**
     * Số ngày phép còn lại (chưa dùng).
     * = totalQuota + carriedOver - usedDays
     */
    public BigDecimal getRemainingDays() {
        return totalQuota.add(carriedOver).subtract(usedDays);
    }

    /**
     * Kiểm tra còn đủ ngày phép để nghỉ.
     *
     * @param requestedDays số ngày muốn nghỉ
     * @return true nếu còn đủ quota
     */
    public boolean hasSufficientBalance(BigDecimal requestedDays) {
        return getRemainingDays().compareTo(requestedDays) >= 0;
    }

    /**
     * Cộng thêm số ngày đã dùng (khi LeaveRequest được APPROVED).
     */
    public void addUsedDays(BigDecimal days) {
        this.usedDays = this.usedDays.add(days);
    }

    /**
     * Trừ lại số ngày đã dùng (khi LeaveRequest bị CANCELLED hoặc REJECTED).
     */
    public void subtractUsedDays(BigDecimal days) {
        this.usedDays = this.usedDays.subtract(days).max(BigDecimal.ZERO);
    }
}
