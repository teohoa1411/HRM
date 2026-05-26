package com.hrms.domain.payroll.dto;

import com.hrms.domain.common.enums.AdjustmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PayrollAdjustmentDto(
        Long id,

        @NotNull(message = "Type is required")
        AdjustmentType type,

        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Amount is required")
        BigDecimal amount,

        Boolean taxable,

        Integer sortOrder
) {}
