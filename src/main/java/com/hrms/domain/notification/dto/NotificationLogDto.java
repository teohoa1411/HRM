package com.hrms.domain.notification.dto;

import com.hrms.domain.common.enums.NotificationTemplateType;

import java.time.LocalDateTime;

public record NotificationLogDto(
        Long id,
        String recipientEmail,
        String subject,
        NotificationTemplateType templateType,
        String referenceType,
        Long referenceId,
        String status,
        String errorMessage,
        LocalDateTime sentAt
) {}
