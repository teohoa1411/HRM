package com.hrms.domain.asset.entity;

import com.hrms.domain.common.entity.BaseCompanyEntity;
import com.hrms.domain.common.enums.AssetCondition;
import com.hrms.domain.common.enums.AssetOwnershipType;
import com.hrms.domain.common.enums.AssetStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Asset extends BaseCompanyEntity {

    @Column(name = "asset_code", nullable = false, length = 30)
    private String assetCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "label_code", length = 50)
    private String labelCode;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_type", nullable = false, length = 20)
    @Builder.Default
    private AssetOwnershipType ownershipType = AssetOwnershipType.COMPANY_OWNED;

    @Column(name = "owner_vendor_id")
    private Long ownerVendorId;

    @Column(name = "supplier_vendor_id")
    private Long supplierVendorId;

    @Column(name = "is_rented", nullable = false)
    @Builder.Default
    private boolean rented = false;

    @Column(name = "rental_notes", columnDefinition = "TEXT")
    private String rentalNotes;

    @Column(name = "purchase_price", precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AssetStatus status = AssetStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_condition", nullable = false, length = 20)
    @Builder.Default
    private AssetCondition assetCondition = AssetCondition.NEW;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
