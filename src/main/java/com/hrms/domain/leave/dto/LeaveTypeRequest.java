package com.hrms.domain.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeaveTypeRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 100) String name,
        Integer maxDays,
        boolean paid,
        boolean autoGrantOnCreate,
        @Size(max = 10) String grantRounding
) {}
