package com.hrms.domain.insurance.dto;

import com.hrms.domain.common.enums.InsuranceType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InsuranceConfigRequest(
        @NotNull(message = "Insurance type is required")
        InsuranceType insuranceType,

        @NotNull(message = "Employee rate is required")
        BigDecimal employeeRate,

        @NotNull(message = "Employer rate is required")
        BigDecimal employerRate,

        BigDecimal minSalary,

        BigDecimal maxSalary,

        @NotNull(message = "Effective from date is required")
        LocalDate effectiveFrom,

        LocalDate effectiveTo
) {}
