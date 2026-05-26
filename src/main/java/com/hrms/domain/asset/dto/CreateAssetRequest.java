package com.hrms.domain.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateAssetRequest(
    @NotBlank String name,
    String serialNumber,
    String labelCode,
    String description,
    @NotNull Long categoryId,
    @NotNull String ownershipType,
    Long ownerVendorId,
    Long supplierVendorId,
    boolean isRented,
    String rentalNotes,
    BigDecimal purchasePrice,
    LocalDate purchaseDate,
    Integer warrantyMonths,
    String assetCondition,
    String notes
) {}
