package com.hrms.domain.account.dto;

import com.hrms.domain.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank @Email(message = "Valid email is required")
        String email,

        @NotNull(message = "Role is required")
        Role role
) {}
