package com.hrms.domain.payroll.dto;

import java.math.BigDecimal;
import java.util.List;

public record UpdatePayrollRequest(
        /** Legacy fields — dùng khi chưa có adjustments */
        BigDecimal bonus,
        BigDecimal deduction,
        String adjustmentNote,

        /** Danh sách khoản phát sinh chi tiết (thay thế bonus/deduction đơn lẻ) */
        List<PayrollAdjustmentDto> adjustments
) {}
