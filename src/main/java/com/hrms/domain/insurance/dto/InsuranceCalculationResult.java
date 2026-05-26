package com.hrms.domain.insurance.dto;

import java.math.BigDecimal;

public record InsuranceCalculationResult(
        BigDecimal insuranceSalary,
        BigDecimal employeeAmount,
        BigDecimal employerAmount,
        BigDecimal bhxhEmployee,
        BigDecimal bhxhEmployer,
        BigDecimal bhytEmployee,
        BigDecimal bhytEmployer,
        BigDecimal bhtnEmployee,
        BigDecimal bhtnEmployer
) {
    public static InsuranceCalculationResult zero() {
        return new InsuranceCalculationResult(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
