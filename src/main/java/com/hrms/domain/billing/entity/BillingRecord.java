package com.hrms.domain.billing.entity;

import com.hrms.domain.common.enums.EmployeeSourceType;
import com.hrms.domain.common.enums.PriceUnit;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "billing_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "employee_id", "billing_month", "period_start"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "job_role_id", nullable = false)
    private Long jobRoleId;

    @Column(name = "billing_month", nullable = false, length = 7)
    private String billingMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 10)
    private EmployeeSourceType sourceType;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "customer_id")
    private Long customerId;

    /**
     * Đoạn thời gian (trong tháng) mà record này áp dụng.
     * Được tính từ EmployeeAssignment cắt vào [monthStart, monthEnd].
     * Một NV có thể có nhiều BillingRecord trong cùng tháng (nếu chuyển dự án giữa tháng
     * hoặc có gap không phân công).
     */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "days_worked", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal daysWorked = BigDecimal.ZERO;

    @Column(name = "days_off", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal daysOff = BigDecimal.ZERO;

    /**
     * Ngày nghỉ phép có hưởng lương (is_paid = true).
     * Tính vào lương nội bộ nhưng KHÔNG tính cho biên bản khách hàng / vendor.
     */
    @Column(name = "paid_leave_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal paidLeaveDays = BigDecimal.ZERO;

    /**
     * Ngày nghỉ không hưởng lương (UNPAID hoặc vượt quota).
     * Trừ lương nội bộ và KHÔNG tính cho khách hàng / vendor.
     */
    @Column(name = "unpaid_leave_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal unpaidLeaveDays = BigDecimal.ZERO;

    @Column(name = "total_workdays", nullable = false)
    @Builder.Default
    private double totalWorkdays = 22;

    /** Đơn vị tính cho cost (vendor/internal salary). */
    @Enumerated(EnumType.STRING)
    @Column(name = "price_unit", nullable = false, length = 10)
    @Builder.Default
    private PriceUnit priceUnit = PriceUnit.MONTHLY;

    /** Đơn vị tính cho sell (customer price). Có thể khác cost unit. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sell_price_unit", nullable = false, length = 10)
    @Builder.Default
    private PriceUnit sellPriceUnit = PriceUnit.MONTHLY;

    @Column(name = "unit_cost_price", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal unitCostPrice = BigDecimal.ZERO;

    @Column(name = "unit_sell_price", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal unitSellPrice = BigDecimal.ZERO;

    @Column(name = "cost_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal costAmount = BigDecimal.ZERO;

    @Column(name = "sell_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal sellAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "calc_status", nullable = false, length = 20)
    @Builder.Default
    private String calcStatus = "CALCULATED";

    @Column(name = "calc_note", length = 500)
    private String calcNote;

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
}
