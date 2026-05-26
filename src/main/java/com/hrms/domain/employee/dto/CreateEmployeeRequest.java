package com.hrms.domain.employee.dto;

import com.hrms.domain.common.enums.EmployeeSourceType;
import com.hrms.domain.common.enums.EmployeeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateEmployeeRequest(
        @NotBlank(message = "Full name is required")
        String fullName,

        @NotNull(message = "Date of birth is required")
        LocalDate dateOfBirth,

        String emailWork,
        String phoneWork,
        String emergencyPhone,

        @NotNull(message = "Source type is required")
        EmployeeSourceType sourceType,

        Long vendorId,

        @NotNull(message = "Job role is required")
        Long jobRoleId,

        Long departmentId,
        Long positionId,
        Long directManagerId,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        EmployeeStatus status,
        String employmentType,
        LocalDate probationEndDate,
        String notes,
        String accountRole
) {}
