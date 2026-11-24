package com.hrms.domain.payroll.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payroll_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "employee_id", "payroll_month"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollRecord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "billing_record_id")
    private Long billingRecordId;

    @Column(name = "source_statement_revision", nullable = false)
    @Builder.Default
    private long sourceStatementRevision = 0L;

    @Column(name = "payroll_month", nullable = false, length = 7)
    private String payrollMonth;

    @Column(name = "base_salary", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal baseSalary = BigDecimal.ZERO;

    @Column(name = "days_worked", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal daysWorked = BigDecimal.ZERO;

    @Column(name = "days_off", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal daysOff = BigDecimal.ZERO;

    @Column(name = "total_workdays", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal totalWorkdays = BigDecimal.valueOf(22);

    @Column(name = "calculated_salary", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal calculatedSalary = BigDecimal.ZERO;

    @Column(name = "overtime_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal overtimeAmount = BigDecimal.ZERO;

    @Column(name = "bonus", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal bonus = BigDecimal.ZERO;

    @Column(name = "deduction", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal deduction = BigDecimal.ZERO;

    /** Mức lương đóng BH (từ InsurancePolicy) */
    @Column(name = "insurance_salary", precision = 18, scale = 2)
    private BigDecimal insuranceSalary;

    /** Tổng tiền BH nhân viên đóng (khấu trừ lương) */
    @Column(name = "insurance_employee_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal insuranceEmployeeAmount = BigDecimal.ZERO;

    /** Tổng tiền BH công ty đóng (ghi nhận chi phí, không trừ lương) */
    @Column(name = "insurance_employer_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal insuranceEmployerAmount = BigDecimal.ZERO;

    /** Chi tiết BHXH NV đóng (8%) */
    @Column(name = "bhxh_employee_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal bhxhEmployeeAmount = BigDecimal.ZERO;

    /** Chi tiết BHXH CTy đóng (17.5%) */
    @Column(name = "bhxh_employer_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal bhxhEmployerAmount = BigDecimal.ZERO;

    /** Chi tiết BHYT NV đóng (1.5%) */
    @Column(name = "bhyt_employee_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal bhytEmployeeAmount = BigDecimal.ZERO;

    /** Chi tiết BHYT CTy đóng (3%) */
    @Column(name = "bhyt_employer_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal bhytEmployerAmount = BigDecimal.ZERO;

    /** Chi tiết BHTN NV đóng (1%) */
    @Column(name = "bhtn_employee_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal bhtnEmployeeAmount = BigDecimal.ZERO;

    /** Chi tiết BHTN CTy đóng (1%) */
    @Column(name = "bhtn_employer_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal bhtnEmployerAmount = BigDecimal.ZERO;

    /** User ID người xác nhận đã trả lương */
    @Column(name = "paid_by")
    private Long paidBy;

    /** Thời điểm xác nhận đã trả lương */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "adjustment_note", columnDefinition = "TEXT")
    private String adjustmentNote;

    /** Danh sách khoản phát sinh (BONUS/DEDUCTION/ALLOWANCE) */
    @OneToMany(mappedBy = "payrollRecordId", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PayrollAdjustment> adjustments = new ArrayList<>();

    @Column(name = "net_salary", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal netSalary = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    /** DRAFT | ADJUSTED | CONFIRMED | EXPORTED | PAID | STALE */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "exported_at")
    private LocalDateTime exportedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    /**
     * Tính lại lương net từ công thức mới:
     * net = calculatedSalary + bonus - deduction - insuranceEmployeeAmount
     *
     * Nếu có adjustments → bonus/deduction tự tính từ SUM(adjustments).
     * Nếu chưa có adjustments → dùng bonus/deduction trực tiếp (backward compatible).
     */
    public void recalculateNetSalary() {
        if (this.adjustments != null && !this.adjustments.isEmpty()) {
            this.bonus = this.adjustments.stream()
                    .filter(a -> "BONUS".equals(a.getType().name()) || "ALLOWANCE".equals(a.getType().name()))
                    .map(PayrollAdjustment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            this.deduction = this.adjustments.stream()
                    .filter(a -> "DEDUCTION".equals(a.getType().name()))
                    .map(PayrollAdjustment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        this.netSalary = this.calculatedSalary
                .add(this.overtimeAmount != null ? this.overtimeAmount : BigDecimal.ZERO)
                .add(this.bonus)
                .subtract(this.deduction)
                .subtract(this.insuranceEmployeeAmount != null ? this.insuranceEmployeeAmount : BigDecimal.ZERO);
    }
}
