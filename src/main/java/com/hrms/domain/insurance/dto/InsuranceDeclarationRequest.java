package com.hrms.domain.insurance.dto;

import com.hrms.domain.common.enums.DeclarationType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InsuranceDeclarationRequest(
        @NotNull(message = "Employee ID is required")
        Long employeeId,

        @NotNull(message = "Declaration type is required")
        DeclarationType declarationType,

        @NotNull(message = "Effective month is required")
        String effectiveMonth,

        @NotNull(message = "Deadline is required")
        LocalDate deadline,

        BigDecimal oldSalary,

        BigDecimal newSalary,

        String reason
) {}
