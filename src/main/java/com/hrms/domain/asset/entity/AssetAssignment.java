package com.hrms.domain.asset.entity;

import com.hrms.domain.common.entity.BaseCompanyEntity;
import com.hrms.domain.common.enums.AssetCondition;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "asset_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AssetAssignment extends BaseCompanyEntity {

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    @Column(name = "returned_date")
    private LocalDate returnedDate;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_on_assign", nullable = false, length = 20)
    private AssetCondition conditionOnAssign;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_on_return", length = 20)
    private AssetCondition conditionOnReturn;

    @Column(name = "monthly_rental_price", precision = 15, scale = 2)
    private BigDecimal monthlyRentalPrice;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
