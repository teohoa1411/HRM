package com.hrms.domain.vendor.entity;

import com.hrms.domain.common.entity.BaseCompanyEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "vendor_contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class VendorContract extends BaseCompanyEntity {

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "contract_number", nullable = false, length = 100)
    private String contractNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "contract_value", precision = 18, scale = 2)
    private BigDecimal contractValue;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "document_s3_key", length = 500)
    private String documentS3Key;

    @Column(name = "renewal_reminder_days", nullable = false)
    @Builder.Default
    private int renewalReminderDays = 30;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
