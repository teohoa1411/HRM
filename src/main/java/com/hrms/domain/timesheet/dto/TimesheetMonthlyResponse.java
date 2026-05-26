package com.hrms.domain.timesheet.dto;

import com.hrms.domain.timesheet.entity.TimesheetMonthly;

import java.time.LocalDateTime;

public record TimesheetMonthlyResponse(
        Long id,
        String yearMonth,
        String description,
        String additions,
        /** true = tháng này đã bị quản lý khóa, nhân sự không được sửa timesheet */
        boolean locked,
        Long lockedBy,
        LocalDateTime lockedAt
) {
    public static TimesheetMonthlyResponse from(TimesheetMonthly m) {
        return new TimesheetMonthlyResponse(
                m.getId(), m.getYearMonth(), m.getDescription(), m.getAdditions(),
                false, null, null);
    }

    public static TimesheetMonthlyResponse from(TimesheetMonthly m, boolean locked, Long lockedBy, LocalDateTime lockedAt) {
        return new TimesheetMonthlyResponse(
                m.getId(), m.getYearMonth(), m.getDescription(), m.getAdditions(),
                locked, lockedBy, lockedAt);
    }

    public static TimesheetMonthlyResponse empty(String yearMonth) {
        return new TimesheetMonthlyResponse(null, yearMonth, null, null, false, null, null);
    }

    public static TimesheetMonthlyResponse empty(String yearMonth, boolean locked, Long lockedBy, LocalDateTime lockedAt) {
        return new TimesheetMonthlyResponse(null, yearMonth, null, null, locked, lockedBy, lockedAt);
    }
}
