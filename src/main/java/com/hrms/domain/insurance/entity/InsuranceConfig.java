package com.hrms.domain.insurance.entity;

import com.hrms.domain.common.enums.InsuranceType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "insurance_config",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "insurance_type", "effective_from"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_type", nullable = false, length = 20)
    private InsuranceType insuranceType;

    /** Tỷ lệ nhân viên đóng (%) */
    @Column(name = "employee_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal employeeRate;

    /** Tỷ lệ công ty đóng (%) */
    @Column(name = "employer_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal employerRate;

    /** Mức sàn lương đóng BH */
    @Column(name = "min_salary", precision = 18, scale = 2)
    private BigDecimal minSalary;

    /** Mức trần lương đóng BH */
    @Column(name = "max_salary", precision = 18, scale = 2)
    private BigDecimal maxSalary;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** NULL = còn hiệu lực */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
