package com.hrms.domain.insurance.dto;

import com.hrms.domain.common.enums.DeclarationStatus;
import com.hrms.domain.common.enums.DeclarationType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InsuranceDeclarationResponse(
        Long id,
        Long employeeId,
        String employeeName,
        String employeeCode,
        DeclarationType declarationType,
        String effectiveMonth,
        LocalDate deadline,
        DeclarationStatus status,
        BigDecimal oldSalary,
        BigDecimal newSalary,
        String reason,
        LocalDateTime createdAt
) {}
