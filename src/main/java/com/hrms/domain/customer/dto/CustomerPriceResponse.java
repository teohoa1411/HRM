package com.hrms.domain.customer.dto;

import com.hrms.domain.common.enums.PriceUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CustomerPriceResponse(
        Long id,
        Long customerId,
        Long jobRoleId,
        String jobRoleName,
        BigDecimal price,
        PriceUnit priceUnit,
        String currency,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String notes,
        LocalDateTime createdAt
) {}
