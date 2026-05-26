package com.hrms.domain.employee.dto;

import com.hrms.domain.common.enums.EmployeeSourceType;
import com.hrms.domain.common.enums.EmployeeStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeResponse(
        Long id,
        String publicId,
        Long companyId,
        String employeeCode,
        Long userId,
        String username,           // login account (from User entity)
        String fullName,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String emailWork,
        String phoneWork,
        String emergencyPhone,
        String avatarS3Key,
        EmployeeSourceType sourceType,
        Long vendorId,
        String vendorName,
        Long jobRoleId,
        String jobRoleName,
        Long departmentId,
        String departmentName,
        Long positionId,
        Long directManagerId,
        LocalDate startDate,
        LocalDate endDate,
        EmployeeStatus status,
        String employmentType,
        LocalDate probationEndDate,
        BigDecimal currentSalary,  // current monthly salary (nullable)
        String notes,
        LocalDateTime createdAt
) {}
