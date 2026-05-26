package com.hrms.domain.alert.dto;

public record AlertCountDto(
        long urgent,
        long warning,
        long info,
        long total,
        long insurance,
        long contract
) {}
