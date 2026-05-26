package com.hrms.domain.contract.dto;

import com.hrms.domain.common.enums.ContractCategory;

import java.time.LocalDateTime;

public record ContractTypeResponse(
        Long id,
        String code,
        String name,
        ContractCategory category,
        boolean hasExpiry,
        Integer defaultDurationMonths,
        int reminderDaysBefore,
        String description,
        boolean active,
        int sortOrder,
        LocalDateTime createdAt
) {}
