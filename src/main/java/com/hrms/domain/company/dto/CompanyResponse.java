package com.hrms.domain.company.dto;

import java.time.LocalDateTime;

public record CompanyResponse(
        Long id,
        String code,
        String name,
        String shortName,
        String taxCode,
        String address,
        String city,
        String country,
        String phone,
        String email,
        String website,
        Long adminUserId,
        String adminUsername,
        String adminEmail,
        boolean active,
        LocalDateTime createdAt,
        /** Only populated once at creation time — null for all other responses */
        String tempAdminPassword
) {}
