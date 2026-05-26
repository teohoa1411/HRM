package com.hrms.domain.insurance.entity;

import com.hrms.domain.common.entity.BaseCompanyEntity;
import com.hrms.domain.common.enums.DeclarationStatus;
import com.hrms.domain.common.enums.DeclarationType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "insurance_declarations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InsuranceDeclaration extends BaseCompanyEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "declaration_type", nullable = false, length = 20)
    private DeclarationType declarationType;

    /** Tháng áp dụng (YYYY-MM) */
    @Column(name = "effective_month", nullable = false, length = 7)
    private String effectiveMonth;

    /** Hạn nộp hồ sơ */
    @Column(name = "deadline", nullable = false)
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DeclarationStatus status = DeclarationStatus.PENDING;

    /** Mức lương BH cũ (dùng cho ADJUSTMENT) */
    @Column(name = "old_salary", precision = 18, scale = 2)
    private BigDecimal oldSalary;

    /** Mức lương BH mới */
    @Column(name = "new_salary", precision = 18, scale = 2)
    private BigDecimal newSalary;

    /** Lý do báo tăng/giảm */
    @Column(name = "reason", length = 500)
    private String reason;
}
