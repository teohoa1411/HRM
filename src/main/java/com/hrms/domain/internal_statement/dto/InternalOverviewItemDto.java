package com.hrms.domain.internal_statement.dto;

import java.math.BigDecimal;

public record InternalOverviewItemDto(
        Long employeeId,
        String employeePublicId,
        String employeeCode,
        String fullName,
        BigDecimal totalRawHours,
        BigDecimal totalAdjustedHours,
        BigDecimal totalAdjustedOtHours,
        BigDecimal totalAdjustedDays,
        int totalLeaveDays,
        int totalHalfLeaveDays,
        String status    // DRAFT | CONFIRMED | NO_DATA
) {}
