package com.hrms.domain.leave.dto;

import com.hrms.domain.leave.entity.LeaveRequest.HalfDayPeriod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateLeaveRequest(
        @NotNull Long leaveTypeId,

        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,

        /**
         * false = nghỉ cả ngày → hệ thống tự tính totalDays = số ngày trong khoảng.
         * true  = nghỉ nửa ngày → totalDays = 0.5, bắt buộc phải có halfDayPeriod.
         */
        boolean halfDay,

        /**
         * Bắt buộc khi halfDay = true.
         * MORNING = nửa buổi sáng, AFTERNOON = nửa buổi chiều.
         * null khi halfDay = false.
         */
        HalfDayPeriod halfDayPeriod,

        @Size(max = 500) String reason
) {}
