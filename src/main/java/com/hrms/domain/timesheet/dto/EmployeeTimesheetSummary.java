package com.hrms.domain.timesheet.dto;

import java.math.BigDecimal;

public record EmployeeTimesheetSummary(
        Long userId,
        String username,
        String displayName,      // fullName or username
        BigDecimal totalHours,
        BigDecimal totalOtHours,
        String description,
        String additions         // JSON string from timesheet_monthly.additions (custom fields)
) {}
