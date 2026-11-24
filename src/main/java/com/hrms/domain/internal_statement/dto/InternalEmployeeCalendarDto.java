package com.hrms.domain.internal_statement.dto;

import java.math.BigDecimal;
import java.util.List;

public record InternalEmployeeCalendarDto(
        Long employeeId,
        String employeePublicId,
        String employeeCode,
        String fullName,
        String yearMonth,
        List<InternalCalendarDayDto> days,
        // summary
        BigDecimal totalExpectedHours,
        BigDecimal totalRawHours,
        BigDecimal totalAdjustedHours,
        BigDecimal totalRawOtHours,
        BigDecimal totalAdjustedOtHours,
        BigDecimal totalAdjustedDays,
        int totalLeaveDays,
        int totalHalfLeaveDays,
        long revision,
        String status
) {}
