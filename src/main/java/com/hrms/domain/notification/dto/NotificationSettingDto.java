package com.hrms.domain.notification.dto;

import com.hrms.domain.common.enums.NotificationTemplateType;

public record NotificationSettingDto(
        Long id,
        NotificationTemplateType templateType,
        boolean enabled,
        String recipientEmails,
        Integer daysBeforeDeadline
) {}
