package com.hrms.domain.statement.dto;

import java.math.BigDecimal;

/**
 * Yêu cầu cập nhật line item trong biên bản.
 *
 * <p>Kế toán có thể điều chỉnh giờ công cho từng nhân sự trong mỗi biên bản
 * độc lập với nhau (INTERNAL / CUSTOMER / VENDOR có thể khác nhau).</p>
 *
 * <p>Ưu tiên: nếu adjustedHours != null → dùng hours. Nếu không có hours
 * thì fallback sang adjustedDays.</p>
 */
public record UpdateLineItemRequest(
        /** Ngày công điều chỉnh (dùng khi chưa migrate sang hours-based) */
        BigDecimal adjustedDays,
        /** Giờ công điều chỉnh — ưu tiên hơn adjustedDays */
        BigDecimal adjustedHours,
        String note
) {}
