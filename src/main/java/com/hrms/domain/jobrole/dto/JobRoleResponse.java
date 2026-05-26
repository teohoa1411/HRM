package com.hrms.domain.jobrole.dto;

public record JobRoleResponse(
        Long id,
        String code,
        String name,
        String category,
        String seniorityLevel,
        String description,
        String skillsRequired,
        boolean active
) {}
