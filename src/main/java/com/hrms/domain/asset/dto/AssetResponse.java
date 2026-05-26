package com.hrms.domain.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AssetResponse(
    Long id,
    String assetCode,
    String name,
    String serialNumber,
    String labelCode,
    String description,
    Long categoryId,
    String categoryName,
    String ownershipType,
    Long ownerVendorId,
    String ownerVendorName,
    Long supplierVendorId,
    String supplierVendorName,
    boolean isRented,
    String rentalNotes,
    BigDecimal purchasePrice,
    LocalDate purchaseDate,
    Integer warrantyMonths,
    String status,
    String assetCondition,
    String notes,
    String currentHolderName,
    Long currentHolderId,
    LocalDateTime createdAt
) {}
