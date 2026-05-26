package com.hrms.domain.internal_statement.dto;

import java.math.BigDecimal;
import java.util.List;

public record InternalEmployeeCalendarDto(
        Long employeeId,
        String employeeCode,
        String fullName,
        String yearMonth,
        List<InternalCalendarDayDto> days,
        // summary
        BigDecimal totalRawHours,
        BigDecimal totalAdjustedHours,
        BigDecimal totalAdjustedDays,
        int totalLeaveDays,
        int totalHalfLeaveDays,
        String status
) {}
