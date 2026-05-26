package com.hrms.domain.alert.dto;

import com.hrms.domain.common.enums.AlertCategory;
import com.hrms.domain.common.enums.AlertSeverity;
import com.hrms.domain.common.enums.AlertStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AlertDto(
        Long id,
        String alertKey,
        AlertCategory category,
        AlertSeverity severity,
        String title,
        String description,
        String referenceType,
        Long referenceId,
        String actionUrl,
        LocalDate dueDate,
        AlertStatus status,
        LocalDateTime createdAt
) {}
