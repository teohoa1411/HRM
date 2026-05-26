package com.hrms.domain.leave.dto;

import java.math.BigDecimal;

public record LeaveBalanceResponse(
        Long id,
        Long employeeId,
        String employeeName,
        String employeeCode,
        Long leaveTypeId,
        String leaveTypeName,
        String leaveTypeCode,
        boolean paid,
        int year,
        BigDecimal totalQuota,
        BigDecimal usedDays,
        BigDecimal carriedOver,
        BigDecimal remainingDays
) {}
