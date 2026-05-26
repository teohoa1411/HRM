package com.hrms.domain.common.enums;

/**
 * Loại khoản phát sinh trong bảng lương.
 */
public enum AdjustmentType {
    BONUS,       // Thưởng (cộng vào lương)
    DEDUCTION,   // Khấu trừ (trừ vào lương)
    ALLOWANCE    // Phụ cấp (cộng vào lương, có thể không chịu thuế)
}
