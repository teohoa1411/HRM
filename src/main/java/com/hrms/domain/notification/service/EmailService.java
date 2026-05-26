package com.hrms.domain.notification.service;

import com.hrms.domain.common.enums.NotificationTemplateType;
import com.hrms.domain.notification.entity.NotificationLog;
import com.hrms.domain.notification.repository.NotificationLogRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service nền tảng gửi email.
 * Hỗ trợ HTML template, async sending, và logging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final NotificationLogRepository logRepository;

    @Value("${spring.mail.username:noreply@hrms.com}")
    private String fromEmail;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    /**
     * Gửi email HTML (async).
     */
    @Async
    public void sendHtmlEmail(Long companyId, String to, String subject, String htmlContent,
                               NotificationTemplateType templateType, String referenceType, Long referenceId) {
        if (!mailEnabled) {
            log.info("Mail disabled. Skipping email to {} subject: {}", to, subject);
            saveLog(companyId, to, subject, templateType, referenceType, referenceId, "SKIPPED", "Mail sending is disabled");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);

            log.info("Email sent to {} subject: {}", to, subject);
            saveLog(companyId, to, subject, templateType, referenceType, referenceId, "SENT", null);
        } catch (Exception e) {
            log.error("Failed to send email to {} subject: {}: {}", to, subject, e.getMessage());
            saveLog(companyId, to, subject, templateType, referenceType, referenceId, "FAILED", e.getMessage());
        }
    }

    /**
     * Kiểm tra đã gửi notification cho reference này trong 24h qua chưa (tránh spam).
     */
    public boolean alreadySentToday(Long companyId, NotificationTemplateType type, Long referenceId) {
        return logRepository.existsByCompanyIdAndTemplateTypeAndReferenceIdAndSentAtAfter(
                companyId, type, referenceId,
                java.time.LocalDateTime.now().minusHours(24));
    }

    private void saveLog(Long companyId, String to, String subject,
                          NotificationTemplateType type, String refType, Long refId,
                          String status, String errorMsg) {
        try {
            logRepository.save(NotificationLog.builder()
                    .companyId(companyId)
                    .recipientEmail(to)
                    .subject(subject)
                    .templateType(type)
                    .referenceType(refType)
                    .referenceId(refId)
                    .status(status)
                    .errorMessage(errorMsg)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to save notification log: {}", e.getMessage());
        }
    }
}
