package com.hrms.domain.alert.scheduler;

import com.hrms.domain.alert.collector.AlertCollector;
import com.hrms.domain.alert.entity.Alert;
import com.hrms.domain.alert.repository.AlertRepository;
import com.hrms.domain.alert.service.AlertService;
import com.hrms.domain.common.enums.AlertStatus;
import com.hrms.domain.common.enums.NotificationTemplateType;
import com.hrms.domain.notification.entity.NotificationSetting;
import com.hrms.domain.notification.repository.NotificationSettingRepository;
import com.hrms.domain.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 1 Scheduled Job duy nhất — chạy 8h sáng MON-FRI.
 * Auto-inject tất cả beans implements AlertCollector.
 * Flow: Collect → Upsert → Auto-resolve → Email → Cleanup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyAlertCollectorJob {

    private final List<AlertCollector> collectors;   // Spring auto-inject tất cả implementations
    private final AlertService alertService;
    private final AlertRepository alertRepository;
    private final NotificationSettingRepository settingRepository;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        log.info("=== DailyAlertCollectorJob START — {} collectors registered ===", collectors.size());

        // Lấy tất cả companies có ít nhất 1 notification setting enabled
        Set<Long> companyIds = settingRepository.findAll().stream()
                .filter(NotificationSetting::isEnabled)
                .map(NotificationSetting::getCompanyId)
                .collect(Collectors.toSet());

        for (Long companyId : companyIds) {
            try {
                processCompany(companyId);
            } catch (Exception e) {
                log.error("DailyAlertCollectorJob failed for company {}: {}", companyId, e.getMessage());
            }
        }

        log.info("=== DailyAlertCollectorJob END ===");
    }

    /** Trigger thủ công (admin) */
    public void runManual() { run(); }

    private void processCompany(Long companyId) {
        List<Alert> allNewAlerts = new ArrayList<>();
        int totalCreated = 0;
        int totalResolved = 0;

        for (AlertCollector collector : collectors) {
            try {
                // Bước 1: Collect alerts mới
                List<Alert> collected = collector.collect(companyId);

                // Bước 2: Upsert — chỉ INSERT cái mới, skip đã tồn tại/dismissed/resolved
                int created = alertService.upsertAlerts(companyId, collected);
                totalCreated += created;

                // Bước 3: Auto-resolve — kiểm tra alerts ACTIVE mà vấn đề đã giải quyết
                List<Alert> activeAlerts = alertRepository.findByCompanyIdAndCategoryAndStatus(
                        companyId, collector.getCategory(), AlertStatus.ACTIVE);
                collector.resolveCompleted(companyId, activeAlerts);
                int resolved = alertService.saveResolvedAlerts(activeAlerts);
                totalResolved += resolved;

                // Thu thập alerts mới để gửi email
                if (created > 0) {
                    allNewAlerts.addAll(collected.stream()
                            .limit(created) // chỉ lấy những cái thực sự mới
                            .toList());
                }

                log.debug("Collector [{}] company {}: {} collected, {} created, {} resolved",
                        collector.getCategory(), companyId, collected.size(), created, resolved);

            } catch (Exception e) {
                log.error("Collector [{}] failed for company {}: {}",
                        collector.getCategory(), companyId, e.getMessage());
            }
        }

        // Bước 4: Email tổng hợp (chỉ gửi nếu có alert mới)
        if (totalCreated > 0) {
            sendCombinedEmail(companyId, allNewAlerts);
        }

        // Bước 5: Cleanup alerts cũ
        int cleaned = alertService.cleanup(companyId);

        log.info("Company {}: {} new alerts, {} resolved, {} cleaned up", companyId, totalCreated, totalResolved, cleaned);
    }

    private void sendCombinedEmail(Long companyId, List<Alert> newAlerts) {
        // Tìm recipient emails từ bất kỳ setting nào enabled
        String recipientEmails = settingRepository.findByCompanyId(companyId).stream()
                .filter(NotificationSetting::isEnabled)
                .map(NotificationSetting::getRecipientEmails)
                .filter(e -> e != null && !e.isBlank())
                .findFirst()
                .orElse(null);

        if (recipientEmails == null) return;

        // Build email HTML tổng hợp — gộp sections từ tất cả collectors
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,sans-serif;color:#333;max-width:600px;'>");
        html.append("<h2 style='color:#1d4ed8;border-bottom:2px solid #1d4ed8;padding-bottom:8px;'>");
        html.append("HRMS — Thông báo hàng ngày</h2>");

        for (AlertCollector collector : collectors) {
            List<Alert> categoryAlerts = newAlerts.stream()
                    .filter(a -> a.getCategory() == collector.getCategory())
                    .toList();
            html.append(collector.buildEmailSection(categoryAlerts));
        }

        html.append("<hr style='border:none;border-top:1px solid #e5e7eb;margin:16px 0;'/>");
        html.append("<p style='font-size:12px;color:#6b7280;'>Đăng nhập HRMS để xử lý. Email tự động, vui lòng không trả lời.</p>");
        html.append("</body></html>");

        long urgent = newAlerts.stream().filter(a -> a.getSeverity() == com.hrms.domain.common.enums.AlertSeverity.URGENT).count();
        String subject = String.format("[HRMS] %d cảnh báo mới (%d khẩn cấp)", newAlerts.size(), urgent);

        for (String email : recipientEmails.split(",")) {
            email = email.trim();
            if (!email.isEmpty()) {
                emailService.sendHtmlEmail(companyId, email, subject, html.toString(),
                        NotificationTemplateType.GENERAL, "ALERT_BATCH", null);
            }
        }
    }
}
