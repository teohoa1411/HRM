package com.hrms.domain.notification.service;

import com.hrms.domain.insurance.dto.InsuranceDeclarationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Tạo HTML content cho các loại email.
 */
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    /**
     * Template cảnh báo deadline báo tăng/giảm BH.
     */
    public String buildInsuranceDeadlineEmail(List<InsuranceDeclarationResponse> upcoming,
                                               List<InsuranceDeclarationResponse> overdue) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:Arial,sans-serif;color:#333;max-width:600px;'>");
        sb.append("<h2 style='color:#1d4ed8;border-bottom:2px solid #1d4ed8;padding-bottom:8px;'>");
        sb.append("HRMS — Cảnh báo Bảo hiểm</h2>");

        if (!overdue.isEmpty()) {
            sb.append("<div style='background:#fef2f2;border-left:4px solid #dc2626;padding:12px 16px;margin:16px 0;border-radius:4px;'>");
            sb.append("<h3 style='color:#dc2626;margin:0 0 8px;'>⚠ QUÁ HẠN (").append(overdue.size()).append(" tờ khai)</h3>");
            sb.append("<table style='width:100%;border-collapse:collapse;font-size:13px;'>");
            sb.append("<tr style='background:#fecaca;'><th style='padding:6px 8px;text-align:left;'>Nhân viên</th>");
            sb.append("<th style='padding:6px 8px;'>Loại</th><th style='padding:6px 8px;'>Hạn nộp</th>");
            sb.append("<th style='padding:6px 8px;'>Lý do</th></tr>");
            for (InsuranceDeclarationResponse d : overdue) {
                sb.append("<tr style='border-bottom:1px solid #fca5a5;'>");
                sb.append("<td style='padding:6px 8px;'>").append(d.employeeName()).append(" (").append(d.employeeCode()).append(")</td>");
                sb.append("<td style='padding:6px 8px;text-align:center;'>").append(typeLabel(d.declarationType().name())).append("</td>");
                sb.append("<td style='padding:6px 8px;text-align:center;color:#dc2626;font-weight:bold;'>").append(d.deadline()).append("</td>");
                sb.append("<td style='padding:6px 8px;font-size:12px;'>").append(d.reason() != null ? d.reason() : "").append("</td>");
                sb.append("</tr>");
            }
            sb.append("</table></div>");
        }

        if (!upcoming.isEmpty()) {
            sb.append("<div style='background:#fffbeb;border-left:4px solid #d97706;padding:12px 16px;margin:16px 0;border-radius:4px;'>");
            sb.append("<h3 style='color:#b45309;margin:0 0 8px;'>Sắp tới deadline (").append(upcoming.size()).append(" tờ khai)</h3>");
            sb.append("<table style='width:100%;border-collapse:collapse;font-size:13px;'>");
            sb.append("<tr style='background:#fef3c7;'><th style='padding:6px 8px;text-align:left;'>Nhân viên</th>");
            sb.append("<th style='padding:6px 8px;'>Loại</th><th style='padding:6px 8px;'>Hạn nộp</th>");
            sb.append("<th style='padding:6px 8px;'>Tháng áp dụng</th></tr>");
            for (InsuranceDeclarationResponse d : upcoming) {
                sb.append("<tr style='border-bottom:1px solid #fde68a;'>");
                sb.append("<td style='padding:6px 8px;'>").append(d.employeeName()).append(" (").append(d.employeeCode()).append(")</td>");
                sb.append("<td style='padding:6px 8px;text-align:center;'>").append(typeLabel(d.declarationType().name())).append("</td>");
                sb.append("<td style='padding:6px 8px;text-align:center;font-weight:bold;'>").append(d.deadline()).append("</td>");
                sb.append("<td style='padding:6px 8px;text-align:center;'>").append(d.effectiveMonth()).append("</td>");
                sb.append("</tr>");
            }
            sb.append("</table></div>");
        }

        sb.append("<p style='font-size:12px;color:#6b7280;margin-top:24px;'>");
        sb.append("Vui lòng đăng nhập hệ thống HRMS → Bảo hiểm để xử lý các tờ khai trên.</p>");
        sb.append("<hr style='border:none;border-top:1px solid #e5e7eb;margin:16px 0;'/>");
        sb.append("<p style='font-size:11px;color:#9ca3af;'>Email tự động từ HRMS. Vui lòng không trả lời email này.</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "INCREASE" -> "Báo tăng";
            case "DECREASE" -> "Báo giảm";
            case "ADJUSTMENT" -> "Điều chỉnh";
            default -> type;
        };
    }
}
