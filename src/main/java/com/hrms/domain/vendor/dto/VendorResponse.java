package com.hrms.domain.vendor.dto;

import com.hrms.domain.common.enums.VendorType;

import java.time.LocalDateTime;

public record VendorResponse(
        Long id,
        Long companyId,
        String code,
        String name,
        VendorType vendorType,
        String contactPerson,
        String contactEmail,
        String contactPhone,
        String address,
        String taxCode,
        Long accountUserId,
        boolean active,
        LocalDateTime createdAt
) {}
