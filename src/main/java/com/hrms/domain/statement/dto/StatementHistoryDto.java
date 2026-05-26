package com.hrms.domain.statement.dto;

import java.time.LocalDateTime;

public record StatementHistoryDto(
        Long id,
        Long statementId,
        String fromStatus,
        String toStatus,
        Long changedBy,
        LocalDateTime changedAt,
        String changeReason
) {}
