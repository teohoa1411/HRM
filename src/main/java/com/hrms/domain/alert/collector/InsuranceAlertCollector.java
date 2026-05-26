package com.hrms.domain.alert.collector;

import com.hrms.domain.alert.entity.Alert;
import com.hrms.domain.common.enums.*;
import com.hrms.domain.insurance.dto.InsuranceDeclarationResponse;
import com.hrms.domain.insurance.entity.InsuranceDeclaration;
import com.hrms.domain.insurance.repository.InsuranceDeclarationRepository;
import com.hrms.domain.insurance.service.InsuranceDeclarationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class InsuranceAlertCollector implements AlertCollector {

    private final InsuranceDeclarationService declarationService;
    private final InsuranceDeclarationRepository declarationRepository;

    @Override
    public AlertCategory getCategory() { return AlertCategory.INSURANCE; }

    @Override
    public NotificationTemplateType getNotificationType() { return NotificationTemplateType.INSURANCE_DEADLINE; }

    @Override
    public List<Alert> collect(Long companyId) {
        List<Alert> alerts = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // Quá hạn → URGENT
        List<InsuranceDeclarationResponse> overdue = declarationService.getOverdue(companyId);
        for (InsuranceDeclarationResponse d : overdue) {
            alerts.add(Alert.builder()
                    .companyId(companyId)
                    .alertKey(Alert.buildKey(AlertCategory.INSURANCE, "DECLARATION", d.id()))
                    .category(AlertCategory.INSURANCE)
                    .severity(AlertSeverity.URGENT)
                    .title("BH quá hạn: " + d.employeeName())
                    .description(typeLabel(d.declarationType().name()) + " — hạn " + d.deadline() + " — " +
                            (d.reason() != null ? d.reason() : ""))
                    .referenceType("DECLARATION")
                    .referenceId(d.id())
                    .actionUrl("/insurance")
                    .dueDate(d.deadline())
                    .status(AlertStatus.ACTIVE)
                    .build());
        }

        // Sắp tới deadline (15 ngày) → WARNING
        List<InsuranceDeclarationResponse> upcoming = declarationService.getUpcoming(companyId, 15);
        for (InsuranceDeclarationResponse d : upcoming) {
            alerts.add(Alert.builder()
                    .companyId(companyId)
                    .alertKey(Alert.buildKey(AlertCategory.INSURANCE, "DECLARATION", d.id()))
                    .category(AlertCategory.INSURANCE)
                    .severity(AlertSeverity.WARNING)
                    .title("BH sắp hạn: " + d.employeeName())
                    .description(typeLabel(d.declarationType().name()) + " — hạn " + d.deadline() +
                            " — tháng " + d.effectiveMonth())
                    .referenceType("DECLARATION")
                    .referenceId(d.id())
                    .actionUrl("/insurance")
                    .dueDate(d.deadline())
                    .status(AlertStatus.ACTIVE)
                    .build());
        }

        return alerts;
    }

    @Override
    public void resolveCompleted(Long companyId, List<Alert> activeAlerts) {
        // Lấy IDs của declarations đang được alert
        Set<Long> alertedIds = activeAlerts.stream()
                .filter(a -> "DECLARATION".equals(a.getReferenceType()))
                .map(Alert::getReferenceId)
                .collect(Collectors.toSet());

        for (Long declId : alertedIds) {
            // Kiểm tra declaration đã APPROVED hoặc SUBMITTED
            declarationRepository.findById(declId).ifPresent(decl -> {
                if (decl.getStatus() == DeclarationStatus.APPROVED || decl.getStatus() == DeclarationStatus.SUBMITTED) {
                    activeAlerts.stream()
                            .filter(a -> declId.equals(a.getReferenceId()) && "DECLARATION".equals(a.getReferenceType()))
                            .forEach(a -> a.setStatus(AlertStatus.RESOLVED));
                }
            });
        }
    }

    @Override
    public String buildEmailSection(List<Alert> alerts) {
        if (alerts.isEmpty()) return "";
        long urgent = alerts.stream().filter(a -> a.getSeverity() == AlertSeverity.URGENT).count();
        long warning = alerts.stream().filter(a -> a.getSeverity() == AlertSeverity.WARNING).count();

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='margin-bottom:20px;'>");
        sb.append("<h3 style='color:#dc2626;margin:0 0 8px;'>Bảo hiểm");
        if (urgent > 0) sb.append(" — ").append(urgent).append(" quá hạn");
        if (warning > 0) sb.append(", ").append(warning).append(" sắp tới");
        sb.append("</h3>");
        sb.append("<table style='width:100%;border-collapse:collapse;font-size:13px;'>");
        sb.append("<tr style='background:#f3f4f6;'><th style='padding:6px 8px;text-align:left;'>Nhân viên</th>");
        sb.append("<th style='padding:6px 8px;'>Mức độ</th><th style='padding:6px 8px;'>Hạn nộp</th></tr>");
        for (Alert a : alerts) {
            String color = a.getSeverity() == AlertSeverity.URGENT ? "#dc2626" : "#d97706";
            sb.append("<tr style='border-bottom:1px solid #e5e7eb;'>");
            sb.append("<td style='padding:6px 8px;'>").append(a.getTitle()).append("</td>");
            sb.append("<td style='padding:6px 8px;text-align:center;color:").append(color).append(";font-weight:bold;'>")
                    .append(a.getSeverity().name()).append("</td>");
            sb.append("<td style='padding:6px 8px;text-align:center;'>").append(a.getDueDate()).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table></div>");
        return sb.toString();
    }

    private String typeLabel(String t) {
        return switch (t) { case "INCREASE" -> "Báo tăng"; case "DECREASE" -> "Báo giảm"; default -> t; };
    }
}
