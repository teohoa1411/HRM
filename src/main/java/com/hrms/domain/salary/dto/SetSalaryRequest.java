package com.hrms.domain.salary.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SetSalaryRequest(
        @NotNull(message = "Monthly salary is required")
        @DecimalMin(value = "0", message = "Salary must be non-negative")
        BigDecimal monthlySalary,

        @NotNull(message = "Effective from date is required")
        LocalDate effectiveFrom,

        String notes
) {}
