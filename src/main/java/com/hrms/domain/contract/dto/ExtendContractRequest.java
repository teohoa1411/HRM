package com.hrms.domain.contract.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExtendContractRequest(
        @NotNull(message = "Additional months is required")
        @Min(value = 1, message = "Must extend at least 1 month")
        Integer additionalMonths,

        String notes
) {}
