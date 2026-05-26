package com.hrms.domain.company.dto;

import jakarta.validation.constraints.NotBlank;

public record PositionRequest(
        @NotBlank(message = "Position code is required") String code,
        @NotBlank(message = "Position name is required") String name,
        int level,
        String description
) {}
