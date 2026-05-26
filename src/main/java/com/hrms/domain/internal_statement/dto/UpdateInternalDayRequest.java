package com.hrms.domain.internal_statement.dto;

import java.math.BigDecimal;

public record UpdateInternalDayRequest(
        BigDecimal adjustedHours,
        String note
) {}
