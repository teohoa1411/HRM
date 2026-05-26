package com.hrms.domain.notification.scheduler;

import com.hrms.domain.common.enums.NotificationTemplateType;
import com.hrms.domain.insurance.dto.InsuranceDeclarationResponse;
import com.hrms.domain.insurance.service.InsuranceDeclarationService;
import com.hrms.domain.notification.entity.NotificationSetting;
import com.hrms.domain.notification.repository.NotificationSettingRepository;
import com.hrms.domain.notification.service.EmailService;
import com.hrms.domain.notification.service.EmailTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Scheduled job chạy hàng ngày 8:00 sáng (giờ VN).
 * Kiểm tra declarations BH sắp tới deadline hoặc quá hạn,
 * gửi email cảnh báo cho HR/kế toán.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsuranceAlertScheduler {

    private final InsuranceDeclarationService declarationService;
    private final NotificationSettingRepository settingRepository;
    private final EmailService emailService;
    private final EmailTemplateService templateService;

    /**
     * Chạy mỗi ngày lúc 8:00 sáng (giờ VN), thứ 2 → thứ 6.
     */
    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
    public void checkInsuranceDeadlines() {
        log.info("Running insurance deadline alert check...");

        // Lấy tất cả company có bật notification INSURANCE_DEADLINE
        List<NotificationSetting> settings = settingRepository
                .findByTemplateTypeAndEnabledTrue(NotificationTemplateType.INSURANCE_DEADLINE);

        for (NotificationSetting setting : settings) {
            try {
                processCompany(setting);
            } catch (Exception e) {
                log.error("Failed to process insurance alerts for company {}: {}",
                        setting.getCompanyId(), e.getMessage());
            }
        }

        log.info("Insurance deadline alert check completed.");
    }

    private void processCompany(NotificationSetting setting) {
        Long companyId = setting.getCompanyId();
        int daysBefore = setting.getDaysBeforeDeadline();

        // Lấy declarations sắp tới deadline
        List<InsuranceDeclarationResponse> upcoming = declarationService.getUpcoming(companyId, daysBefore);

        // Lấy declarations quá hạn
        List<InsuranceDeclarationResponse> overdue = declarationService.getOverdue(companyId);

        if (upcoming.isEmpty() && overdue.isEmpty()) {
            log.debug("No insurance alerts for company {}", companyId);
            return;
        }

        // Lấy danh sách email nhận
        String recipientEmails = setting.getRecipientEmails();
        if (recipientEmails == null || recipientEmails.isBlank()) {
            log.warn("No recipient emails configured for company {} INSURANCE_DEADLINE", companyId);
            return;
        }

        // Tạo email content
        String htmlContent = templateService.buildInsuranceDeadlineEmail(upcoming, overdue);
        String subject = String.format("[HRMS] Cảnh báo BH: %d sắp tới hạn, %d quá hạn",
                upcoming.size(), overdue.size());

        // Gửi cho từng recipient
        List<String> emails = Arrays.stream(recipientEmails.split(","))
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .toList();

        for (String email : emails) {
            emailService.sendHtmlEmail(companyId, email, subject, htmlContent,
                    NotificationTemplateType.INSURANCE_DEADLINE,
                    "INSURANCE_DECLARATION", null);
        }

        log.info("Sent insurance alerts for company {}: {} upcoming, {} overdue, to {} recipients",
                companyId, upcoming.size(), overdue.size(), emails.size());
    }
}
