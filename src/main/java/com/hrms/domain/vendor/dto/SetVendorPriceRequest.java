package com.hrms.domain.vendor.dto;

import com.hrms.domain.common.enums.PriceUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SetVendorPriceRequest(
        @NotNull(message = "Job role is required")
        Long jobRoleId,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0", message = "Price must be non-negative")
        BigDecimal price,

        PriceUnit priceUnit,
        String currency,

        @NotNull(message = "Effective from date is required")
        LocalDate effectiveFrom,

        String notes
) {}
