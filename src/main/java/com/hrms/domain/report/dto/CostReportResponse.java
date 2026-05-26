package com.hrms.domain.report.dto;

import java.math.BigDecimal;

public record CostReportResponse(
        String fromDate,
        String toDate,
        BigDecimal totalCost,
        BigDecimal totalRevenue,
        BigDecimal profit,
        long employeeCount,
        String currency
) {}
