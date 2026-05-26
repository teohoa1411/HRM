package com.hrms.web.controller;

import com.hrms.domain.notification.dto.NotificationLogDto;
import com.hrms.domain.notification.dto.NotificationSettingDto;
import com.hrms.domain.notification.entity.NotificationLog;
import com.hrms.domain.notification.repository.NotificationLogRepository;
import com.hrms.domain.notification.scheduler.InsuranceAlertScheduler;
import com.hrms.domain.notification.service.NotificationSettingService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
public class NotificationController {

    private final NotificationLogRepository logRepository;
    private final NotificationSettingService settingService;
    private final InsuranceAlertScheduler alertScheduler;

    /** Xem lá»‹ch sá»­ gá»­i email */
    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<NotificationLogDto>>> getLogs() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        List<NotificationLogDto> logs = logRepository.findByCompanyIdOrderBySentAtDesc(companyId)
                .stream().map(this::toLogDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(logs));
    }

    /** Xem cáº¥u hÃ¬nh notification */
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<List<NotificationSettingDto>>> getSettings() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(settingService.getSettings(companyId)));
    }

    /** Cáº­p nháº­t cáº¥u hÃ¬nh notification */
    @PutMapping("/settings")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<NotificationSettingDto>> updateSetting(
            @RequestBody NotificationSettingDto dto) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(settingService.upsert(companyId, dto)));
    }

    /** Seed default settings */
    @PostMapping("/settings/seed")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> seedDefaults() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        settingService.seedDefaults(companyId);
        return ResponseEntity.ok(ApiResponse.ok("Notification settings seeded"));
    }

    /** Trigger thá»§ cÃ´ng kiá»ƒm tra BH (admin/debug) */
    @PostMapping("/trigger-insurance-check")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> triggerInsuranceCheck() {
        alertScheduler.checkInsuranceDeadlines();
        return ResponseEntity.ok(ApiResponse.ok("Insurance alert check triggered"));
    }

    private NotificationLogDto toLogDto(NotificationLog e) {
        return new NotificationLogDto(
                e.getId(), e.getRecipientEmail(), e.getSubject(),
                e.getTemplateType(), e.getReferenceType(), e.getReferenceId(),
                e.getStatus(), e.getErrorMessage(), e.getSentAt());
    }
}
