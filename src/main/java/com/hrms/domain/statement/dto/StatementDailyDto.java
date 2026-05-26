package com.hrms.domain.statement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatementDailyDto(
        Long id,
        LocalDate workDate,
        String dayOfWeek,
        boolean weekend,
        BigDecimal internalHours,
        BigDecimal customerHours,
        boolean leaveDay,
        boolean halfLeave,
        boolean paidLeave,
        String leaveTypeName,
        String accountantNote
) {}
