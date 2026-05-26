package com.hrms.domain.salary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SalaryResponse(
        Long id,
        Long employeeId,
        BigDecimal monthlySalary,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String notes,
        LocalDateTime createdAt
) {}
