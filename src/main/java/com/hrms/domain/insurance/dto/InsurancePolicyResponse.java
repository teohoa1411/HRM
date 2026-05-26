package com.hrms.domain.insurance.dto;

import com.hrms.domain.common.enums.InsurancePolicyStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InsurancePolicyResponse(
        Long id,
        Long employeeId,
        String employeeName,
        String employeeCode,
        BigDecimal insuranceSalary,
        String socialInsuranceNumber,
        LocalDate startDate,
        LocalDate endDate,
        InsurancePolicyStatus status,
        String notes,
        LocalDateTime createdAt
) {}
