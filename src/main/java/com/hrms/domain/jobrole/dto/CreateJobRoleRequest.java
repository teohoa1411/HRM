package com.hrms.domain.jobrole.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateJobRoleRequest(
        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "Code must be SCREAMING_SNAKE_CASE")
        String code,

        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Category is required")
        String category,

        String seniorityLevel,
        String description,
        String skillsRequired
) {}
