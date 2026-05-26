package com.hrms.domain.customer.dto;

import java.time.LocalDateTime;

public record CustomerResponse(
        Long id,
        Long companyId,
        String code,
        String vendorCode,
        String name,
        String contactPerson,
        String contactEmail,
        String contactPhone,
        String address,
        String taxCode,
        String notes,
        boolean active,
        LocalDateTime createdAt
) {}
