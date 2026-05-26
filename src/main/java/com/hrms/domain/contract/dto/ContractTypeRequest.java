package com.hrms.domain.contract.dto;

import com.hrms.domain.common.enums.ContractCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContractTypeRequest(
        @NotBlank(message = "Code is required")
        String code,

        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Category is required")
        ContractCategory category,

        boolean hasExpiry,

        Integer defaultDurationMonths,

        Integer reminderDaysBefore,

        String description,

        Integer sortOrder
) {}
