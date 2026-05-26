package com.hrms.domain.leave.dto;

import com.hrms.domain.leave.entity.LeaveRequest.HalfDayPeriod;
import com.hrms.domain.leave.entity.LeaveRequest.LeaveStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveRequestResponse(
        Long id,
        Long companyId,
        Long employeeId,
        String employeeName,
        Long leaveTypeId,
        String leaveTypeName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalDays,
        boolean halfDay,
        HalfDayPeriod halfDayPeriod,
        String reason,
        LeaveStatus status,
        Long approvedBy,
        LocalDateTime approvedAt,
        String rejectReason,
        LocalDateTime createdAt
) {}
