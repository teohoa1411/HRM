package com.hrms.domain.common.enums;

/**
 * Các loại template thông báo email.
 */
public enum NotificationTemplateType {
    INSURANCE_DEADLINE,     // Cảnh báo deadline báo tăng/giảm BH
    INSURANCE_OVERDUE,      // Cảnh báo quá hạn báo tăng/giảm BH
    CONTRACT_EXPIRY,        // Nhắc hợp đồng sắp hết hạn
    PAYROLL_CONFIRMED,      // Thông báo bảng lương đã xác nhận
    LEAVE_APPROVED,         // Đơn nghỉ phép được duyệt
    GENERAL                 // Thông báo chung
}
