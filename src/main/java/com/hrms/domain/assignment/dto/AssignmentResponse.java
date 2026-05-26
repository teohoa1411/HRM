package com.hrms.domain.assignment.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AssignmentResponse(
        Long id,
        Long companyId,
        Long employeeId,
        String employeeName,
        Long customerId,
        String customerName,
        String projectName,
        String projectCode,
        LocalDate startDate,
        LocalDate endDate,
        String notes,
        String clientUsername,
        String workLocation,
        LocalDateTime createdAt
) {}
