package com.hrms.domain.insurance.dto;

import com.hrms.domain.common.enums.InsuranceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InsuranceConfigResponse(
        Long id,
        InsuranceType insuranceType,
        BigDecimal employeeRate,
        BigDecimal employerRate,
        BigDecimal minSalary,
        BigDecimal maxSalary,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        LocalDateTime createdAt
) {}
