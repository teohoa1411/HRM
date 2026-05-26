package com.hrms.domain.contract.entity;

import com.hrms.domain.common.entity.BaseCompanyEntity;
import com.hrms.domain.common.enums.ContractStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employee_contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class EmployeeContract extends BaseCompanyEntity {

    @Builder.Default
    @Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 36)
    private String publicId = UUID.randomUUID().toString();

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "contract_type_id", nullable = false)
    private Long contractTypeId;

    /** Số hợp đồng / mã biên bản (nhập tay, nullable cho biên bản cam kết) */
    @Column(name = "contract_number", length = 100)
    private String contractNumber;

    /** Ngày ký */
    @Column(name = "signed_date")
    private LocalDate signedDate;

    /** Ngày có hiệu lực */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Ngày hết hạn, null nếu vô thời hạn hoặc cam kết vĩnh viễn */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** Thời hạn (tháng), null nếu vô thời hạn */
    @Column(name = "duration_months")
    private Integer durationMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ContractStatus status = ContractStatus.ACTIVE;

    /** FK sang Document entity (file scan), nullable - có thể chưa upload */
    @Column(name = "document_id")
    private Long documentId;

    /** Ghi chú */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
