package com.hrms.domain.vendor.dto;

import com.hrms.domain.common.enums.PriceUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record VendorPriceResponse(
        Long id,
        Long vendorId,
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
