package com.hrms.domain.account.dto;

import com.hrms.domain.common.enums.Role;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        Role role,
        Long companyId,
        boolean active,
        LocalDateTime lockedUntil,
        LocalDateTime lastLoginAt,
        boolean mustChangePassword,
        LocalDateTime createdAt
) {}
