package com.hrms.domain.timesheet.dto;

import com.hrms.domain.timesheet.entity.Timesheet;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TimesheetEntryResponse(
        Long id,
        String projectKey,
        String projectId,
        String projectName,
        LocalDate workDate,
        BigDecimal hours,
        BigDecimal otHours,
        String note,
        /** true = entry được tạo tự động bởi leave, không cho phép nhân sự sửa */
        boolean lockedByLeave
) {
    public static TimesheetEntryResponse from(Timesheet t) {
        return new TimesheetEntryResponse(
                t.getId(), t.getProjectKey(), t.getProjectId(), t.getProjectName(),
                t.getWorkDate(), t.getHours(), t.getOtHours(), t.getNote(),
                t.isLockedByLeave());
    }
}
