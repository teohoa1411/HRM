package com.hrms.domain.common.enums;

/**
 * Trạng thái của tờ khai báo tăng/giảm BH.
 */
public enum DeclarationStatus {
    PENDING,     // Chờ xử lý
    SUBMITTED,   // Đã nộp hồ sơ
    APPROVED,    // Cơ quan BH đã duyệt
    REJECTED     // Bị từ chối
}
