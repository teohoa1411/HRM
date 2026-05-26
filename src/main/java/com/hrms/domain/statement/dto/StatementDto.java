package com.hrms.domain.statement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StatementDto(
        Long id,
        String statementType,
        Long recipientId,
        String recipientName,
        String statementMonth,
        int totalEmployees,
        BigDecimal totalDaysWorked,
        BigDecimal totalAmount,
        String currency,
        /** DRAFT | EXPORTED | SENT | CONFIRMED | STALE */
        String status,
        LocalDateTime sentAt,
        Long sentBy,
        LocalDateTime confirmedAt,
        Long confirmedBy,
        LocalDateTime exportedAt,
        /** Lý do STALE — mô tả timesheet nào bị thay đổi sau khi xuất */
        String staleReason,
        String note,
        List<StatementLineItemDto> lineItems
) {}
