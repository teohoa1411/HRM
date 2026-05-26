package com.hrms.domain.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MonthlySummaryDto(
        String month,
        BigDecimal totalRevenue,
        BigDecimal totalPayrollCost,
        BigDecimal totalVendorCost,
        BigDecimal totalProfit,
        BigDecimal profitMargin,
        int totalEmployees,
        LocalDateTime lastCalculatedAt
) {}
