package com.hrms.domain.insurance.entity;

import com.hrms.domain.common.entity.BaseCompanyEntity;
import com.hrms.domain.common.enums.InsurancePolicyStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "insurance_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InsurancePolicy extends BaseCompanyEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** Mức lương đóng BH (khác lương thực tế) */
    @Column(name = "insurance_salary", nullable = false, precision = 18, scale = 2)
    private BigDecimal insuranceSalary;

    /** Số sổ BHXH */
    @Column(name = "social_insurance_number", length = 20)
    private String socialInsuranceNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** NULL = đang tham gia */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InsurancePolicyStatus status = InsurancePolicyStatus.ACTIVE;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
