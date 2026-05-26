package com.hrms.domain.asset.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReturnAssetRequest(
    @NotNull LocalDate returnedDate,
    @NotNull String conditionOnReturn,
    String notes
) {}
