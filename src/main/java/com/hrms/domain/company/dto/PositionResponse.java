package com.hrms.domain.company.dto;

public record PositionResponse(
        Long id,
        Long companyId,
        String code,
        String name,
        int level,
        String description,
        boolean active
) {}
