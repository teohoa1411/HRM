package com.hrms.domain.timesheet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyTimesheetFieldRequest(
        @NotBlank @Size(max = 100)
        String fieldName,

        @NotBlank @Size(max = 50) @Pattern(regexp = "[a-z0-9_]+")
        String fieldKey,

        @Pattern(regexp = "text|number|checkbox")
        String fieldType,

        boolean required,
        int sortOrder
) {}
