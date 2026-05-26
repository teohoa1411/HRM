package com.hrms.domain.statement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "statement_line_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"statement_id", "employee_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StatementLineItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id", nullable = false)
    private Statement statement;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "billing_record_id")
    private Long billingRecordId;

    @Column(name = "original_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal originalDays = BigDecimal.ZERO;

    @Column(name = "adjusted_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal adjustedDays = BigDecimal.ZERO;

    /**
     * Giờ công gốc từ timesheet (không thay đổi sau khi tạo biên bản).
     * Được tính từ: daysWorked × hoursPerDay + paid_leave × hoursPerDay (cho INTERNAL)
     */
    @Column(name = "original_hours", nullable = false, precision = 7, scale = 1)
    @Builder.Default
    private BigDecimal originalHours = BigDecimal.ZERO;

    /**
     * Giờ công sau điều chỉnh của kế toán cho biên bản này.
     * Mỗi loại biên bản (INTERNAL / CUSTOMER / VENDOR) điều chỉnh độc lập.
     */
    @Column(name = "adjusted_hours", nullable = false, precision = 7, scale = 1)
    @Builder.Default
    private BigDecimal adjustedHours = BigDecimal.ZERO;

    /**
     * Số giờ/ngày để quy đổi giữa ngày và giờ (mặc định 8.0).
     */
    @Column(name = "hours_per_day", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    private BigDecimal hoursPerDay = BigDecimal.valueOf(8.0);

    @Column(name = "unit_sell_price", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal unitSellPrice = BigDecimal.ZERO;

    @Column(name = "original_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal originalAmount = BigDecimal.ZERO;

    @Column(name = "adjusted_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal adjustedAmount = BigDecimal.ZERO;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    /**
     * Tính lại các amount dựa trên hours.
     * Công thức: amount = (hours / hoursPerDay) × unitSellPrice
     *
     * Gọi sau khi thay đổi adjustedHours hoặc unitSellPrice.
     */
    public void recalculateAmounts() {
        BigDecimal divisor = (this.hoursPerDay != null && this.hoursPerDay.compareTo(BigDecimal.ZERO) > 0)
                ? this.hoursPerDay
                : BigDecimal.valueOf(8.0);

        // Quy đổi hours → days để tính amount (giữ tương thích với đơn giá theo ngày)
        this.originalDays   = this.originalHours.divide(divisor, 1, java.math.RoundingMode.HALF_UP);
        this.adjustedDays   = this.adjustedHours.divide(divisor, 1, java.math.RoundingMode.HALF_UP);

        this.originalAmount = this.originalDays.multiply(this.unitSellPrice)
                                  .setScale(2, java.math.RoundingMode.HALF_UP);
        this.adjustedAmount = this.adjustedDays.multiply(this.unitSellPrice)
                                  .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Khởi tạo hours từ days khi tạo mới line item (backward-compat).
     * Gọi khi tạo từ BillingRecord (dữ liệu cũ chưa có hours).
     */
    public void initHoursFromDays() {
        BigDecimal divisor = (this.hoursPerDay != null && this.hoursPerDay.compareTo(BigDecimal.ZERO) > 0)
                ? this.hoursPerDay
                : BigDecimal.valueOf(8.0);
        this.originalHours = this.originalDays.multiply(divisor);
        this.adjustedHours = this.adjustedDays.multiply(divisor);
    }
}
