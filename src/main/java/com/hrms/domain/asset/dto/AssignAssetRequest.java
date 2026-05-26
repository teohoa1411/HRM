package com.hrms.domain.asset.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssignAssetRequest(
    @NotNull Long employeeId,
    @NotNull LocalDate assignedDate,
    @NotNull String conditionOnAssign,
    BigDecimal monthlyRentalPrice,
    String notes
) {}
