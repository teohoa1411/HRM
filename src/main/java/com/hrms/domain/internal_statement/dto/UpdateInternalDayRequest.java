package com.hrms.domain.internal_statement.dto;

import java.math.BigDecimal;

public record UpdateInternalDayRequest(
        BigDecimal adjustedHours,
        BigDecimal adjustedOtHours,
        BigDecimal otMultiplier,
        String otAdjustmentReason,
        String note
) {}
