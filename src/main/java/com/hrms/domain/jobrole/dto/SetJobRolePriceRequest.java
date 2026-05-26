package com.hrms.domain.jobrole.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SetJobRolePriceRequest(
        @NotNull(message = "Cost price is required")
        @DecimalMin(value = "0", message = "Cost price must be non-negative")
        BigDecimal costPrice,

        @NotNull(message = "Sell price is required")
        @DecimalMin(value = "0", message = "Sell price must be non-negative")
        BigDecimal sellPrice,

        String currency,
        String billingUnit,

        @NotNull(message = "Effective from date is required")
        LocalDate effectiveFrom,

        String notes
) {}
