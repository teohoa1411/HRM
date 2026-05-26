package com.hrms.domain.insurance.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InsurancePolicyRequest(
        @NotNull(message = "Employee ID is required")
        Long employeeId,

        @NotNull(message = "Insurance salary is required")
        BigDecimal insuranceSalary,

        String socialInsuranceNumber,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        LocalDate endDate,

        String notes
) {}
