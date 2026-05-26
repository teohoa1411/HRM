package com.hrms.domain.leave.dto;

import com.hrms.domain.leave.entity.LeaveRequest.HalfDayPeriod;
import com.hrms.domain.leave.entity.LeaveRequest.LeaveStatus;

import java.time.LocalDate;

/**
 * Một ngày có đơn nghỉ (APPROVED hoặc PENDING) — dùng để timesheet UI hiện icon trên header.
 * halfDay = false → cả ngày; halfDay = true → nửa ngày (halfDayPeriod chỉ rõ sáng/chiều).
 * status = APPROVED → đã duyệt; PENDING → chờ duyệt.
 */
public record LeaveBlockResponse(
        LocalDate date,
        boolean halfDay,
        HalfDayPeriod halfDayPeriod,  // null khi fullDay
        LeaveStatus status
) {}
