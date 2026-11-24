package com.hrms.domain.payroll.dto;

public record PayrollReadinessEmployeeDto(
        Long employeeId, String employeePublicId, String employeeCode, String fullName, String reason
) {}
