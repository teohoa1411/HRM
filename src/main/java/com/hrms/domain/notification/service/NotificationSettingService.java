package com.hrms.domain.notification.service;

import com.hrms.domain.common.enums.NotificationTemplateType;
import com.hrms.domain.notification.dto.NotificationSettingDto;
import com.hrms.domain.notification.entity.NotificationSetting;
import com.hrms.domain.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final NotificationSettingRepository repository;

    @Transactional(readOnly = true)
    public List<NotificationSettingDto> getSettings(Long companyId) {
        return repository.findByCompanyId(companyId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public NotificationSettingDto upsert(Long companyId, NotificationSettingDto dto) {
        NotificationSetting entity = repository
                .findByCompanyIdAndTemplateType(companyId, dto.templateType())
                .orElse(NotificationSetting.builder()
                        .companyId(companyId)
                        .templateType(dto.templateType())
                        .build());

        entity.setEnabled(dto.enabled());
        entity.setRecipientEmails(dto.recipientEmails());
        if (dto.daysBeforeDeadline() != null) {
            entity.setDaysBeforeDeadline(dto.daysBeforeDeadline());
        }

        return toDto(repository.save(entity));
    }

    /**
     * Seed default settings cho company mới.
     */
    @Transactional
    public void seedDefaults(Long companyId) {
        for (NotificationTemplateType type : NotificationTemplateType.values()) {
            if (!repository.findByCompanyIdAndTemplateType(companyId, type).isPresent()) {
                repository.save(NotificationSetting.builder()
                        .companyId(companyId)
                        .templateType(type)
                        .enabled(true)
                        .daysBeforeDeadline(7)
                        .build());
            }
        }
    }

    private NotificationSettingDto toDto(NotificationSetting e) {
        return new NotificationSettingDto(
                e.getId(), e.getTemplateType(), e.isEnabled(),
                e.getRecipientEmails(), e.getDaysBeforeDeadline());
    }
}
