package com.hrms.domain.account.dto;

import com.hrms.domain.common.enums.Role;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        Long userId,
        String username,
        String email,
        Role role,
        Long companyId,
        boolean mustChangePassword
) {}
