package com.hrms.domain.alert.collector;

import com.hrms.domain.alert.entity.Alert;
import com.hrms.domain.common.enums.AlertCategory;
import com.hrms.domain.common.enums.NotificationTemplateType;

import java.util.List;

/**
 * Interface chung cho tất cả loại cảnh báo.
 * Mỗi loại (BH, HĐ, nghỉ phép...) implement 1 class.
 * DailyAlertCollectorJob tự inject tất cả beans.
 *
 * Thêm loại cảnh báo mới = tạo 1 class implements AlertCollector.
 * Không sửa job, không sửa DB, không sửa Dashboard.
 */
public interface AlertCollector {

    /** Tên category: INSURANCE, CONTRACT... */
    AlertCategory getCategory();

    /** Enum type cho notification log */
    NotificationTemplateType getNotificationType();

    /**
     * Query DB, trả về danh sách Alert cần tạo.
     * Mỗi alert phải có alertKey unique.
     * Job sẽ upsert: chỉ INSERT nếu chưa tồn tại.
     */
    List<Alert> collect(Long companyId);

    /**
     * Kiểm tra alerts ACTIVE mà vấn đề gốc đã giải quyết → set RESOLVED.
     * VD: declaration đã APPROVED → resolve alert tương ứng.
     */
    void resolveCompleted(Long companyId, List<Alert> activeAlerts);

    /**
     * Tạo HTML section cho email tổng hợp.
     * Trả về 1 phần HTML (không phải full email — job sẽ gộp các sections).
     */
    String buildEmailSection(List<Alert> alerts);
}
