package com.hrms.domain.finance.dto;

import java.math.BigDecimal;

public record EmployeeFinancialDto(
        Long employeeId,
        String employeeCode,
        String fullName,
        String sourceType,
        String customerName,
        BigDecimal billedDays,
        BigDecimal revenueAmount,
        BigDecimal netCostAmount,
        BigDecimal profitAmount,
        BigDecimal profitMargin
) {}
