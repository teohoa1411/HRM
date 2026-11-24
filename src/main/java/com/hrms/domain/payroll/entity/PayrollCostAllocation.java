package com.hrms.domain.payroll.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Allocation of an immutable payroll run.  This is deliberately independent
 * from BillingRecord: re-generating a customer statement must never alter cost.
 */
@Entity
@Table(name = "payroll_cost_allocations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"payroll_record_id", "customer_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollCostAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "payroll_record_id", nullable = false) private Long payrollRecordId;
    @Column(name = "employee_id", nullable = false) private Long employeeId;
    @Column(name = "payroll_month", nullable = false, length = 7) private String payrollMonth;
    @Column(name = "customer_id") private Long customerId;
    @Column(name = "allocation_ratio", nullable = false, precision = 9, scale = 6) private BigDecimal allocationRatio;
    @Column(name = "allocated_amount", nullable = false, precision = 18, scale = 2) private BigDecimal allocatedAmount;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
