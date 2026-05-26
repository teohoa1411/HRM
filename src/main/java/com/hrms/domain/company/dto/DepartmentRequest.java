package com.hrms.domain.company.dto;

import jakarta.validation.constraints.NotBlank;

public record DepartmentRequest(
        @NotBlank(message = "Department code is required")
        String code,

        @NotBlank(message = "Department name is required")
        String name,

        Long parentDeptId,
        Long managerEmployeeId,
        String description,
        int displayOrder,
        Boolean active
) {}
