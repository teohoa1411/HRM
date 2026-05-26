package com.hrms.domain.assignment.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AssignmentRequest(
        @NotNull Long customerId,
        String projectName,
        String projectCode,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        String notes,
        String clientUsername,
        String workLocation
) {}
