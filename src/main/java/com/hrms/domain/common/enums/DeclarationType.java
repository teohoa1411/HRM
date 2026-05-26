package com.hrms.domain.common.enums;

/**
 * Loại khai báo bảo hiểm.
 */
public enum DeclarationType {
    INCREASE,    // Báo tăng (NV mới, ký HĐ chính thức)
    DECREASE,    // Báo giảm (NV nghỉ việc)
    ADJUSTMENT   // Điều chỉnh mức đóng
}
