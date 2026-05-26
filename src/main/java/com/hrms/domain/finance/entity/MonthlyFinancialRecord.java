package com.hrms.domain.finance.entity;

import com.hrms.domain.common.enums.EmployeeSourceType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_financial_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "employee_id", "record_month", "customer_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MonthlyFinancialRecord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "record_month", nullable = false, length = 7)
    private String recordMonth;

    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 10)
    private EmployeeSourceType sourceType;

    @Column(name = "vendor_id")
    private Long vendorId;

    // Phía THU (Revenue)
    @Column(name = "billed_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal billedDays = BigDecimal.ZERO;

    @Column(name = "unit_sell_price", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal unitSellPrice = BigDecimal.ZERO;

    @Column(name = "revenue_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal revenueAmount = BigDecimal.ZERO;

    // Phía CHI (Cost)
    @Column(name = "worked_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal workedDays = BigDecimal.ZERO;

    @Column(name = "unit_cost_price", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal unitCostPrice = BigDecimal.ZERO;

    @Column(name = "base_cost_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal baseCostAmount = BigDecimal.ZERO;

    @Column(name = "bonus_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal bonusAmount = BigDecimal.ZERO;

    @Column(name = "deduction_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal deductionAmount = BigDecimal.ZERO;

    @Column(name = "net_cost_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal netCostAmount = BigDecimal.ZERO;

    // Profit
    @Column(name = "profit_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal profitAmount = BigDecimal.ZERO;

    @Column(name = "profit_margin", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal profitMargin = BigDecimal.ZERO;

    /** DRAFT | CONFIRMED */
    @Column(name = "status", nullable = false, length = 15)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
