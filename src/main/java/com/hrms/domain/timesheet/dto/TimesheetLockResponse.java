package com.hrms.domain.timesheet.dto;

import com.hrms.domain.timesheet.entity.TimesheetPeriodLock;

import java.time.LocalDateTime;

public record TimesheetLockResponse(
        Long id,
        Long companyId,
        String yearMonth,
        boolean locked,
        Long lockedBy,
        LocalDateTime lockedAt,
        boolean accountantConfirmed,
        Long accountantConfirmedBy,
        LocalDateTime accountantConfirmedAt
) {
    public static TimesheetLockResponse from(TimesheetPeriodLock lock) {
        return new TimesheetLockResponse(
                lock.getId(), lock.getCompanyId(), lock.getYearMonth(),
                lock.isLocked(), lock.getLockedBy(), lock.getLockedAt(),
                lock.isAccountantConfirmed(),
                lock.getAccountantConfirmedBy(),
                lock.getAccountantConfirmedAt());
    }

    public static TimesheetLockResponse unlocked(Long companyId, String yearMonth) {
        return new TimesheetLockResponse(null, companyId, yearMonth,
                false, null, null, false, null, null);
    }
}
