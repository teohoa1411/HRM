package com.hrms.domain.company.dto;

public record DepartmentResponse(
        Long id,
        Long companyId,
        String code,
        String name,
        Long parentDeptId,
        Long managerEmployeeId,
        String description,
        int displayOrder,
        boolean active
) {}
