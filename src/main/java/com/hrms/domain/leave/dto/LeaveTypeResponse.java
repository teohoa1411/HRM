package com.hrms.domain.leave.dto;

public record LeaveTypeResponse(
        Long id, Long companyId, String code, String name,
        Integer maxDays, boolean paid, boolean active,
        boolean autoGrantOnCreate, String grantRounding
) {}
