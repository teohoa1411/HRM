package com.hrms.domain.internal_statement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InternalCalendarDayDto(
        LocalDate workDate,
        String dayOfWeek,           // MON, TUE, ...
        boolean weekend,
        BigDecimal expectedHours,
        BigDecimal rawHours,        // từ timesheet
        BigDecimal adjustedHours,   // HR override
        BigDecimal rawOtHours,
        BigDecimal adjustedOtHours,
        BigDecimal otMultiplier,
        String otAdjustmentReason,
        boolean leaveDay,           // nghỉ phép cả ngày
        boolean halfLeave,          // nghỉ phép nửa ngày
        boolean paidLeave,          // có lương
        String leaveTypeName,       // tên loại phép
        String timesheetNote,       // note từ timesheet gốc
        String hrNote,              // note của HR trong biên bản nội bộ
        Long entryId                // null nếu chưa có entry
) {}
