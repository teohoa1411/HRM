package com.hrms.domain.statement.dto;

import java.math.BigDecimal;
import java.util.List;

public record StatementLineItemDto(
        Long id,
        Long employeeId,
        /** User ID của nhân sự (Employee.user_id) — FE dùng để fetch raw timesheet. */
        Long userId,
        String employeeCode,
        String fullName,
        String jobRoleName,
        BigDecimal originalDays,
        BigDecimal adjustedDays,
        /** Giờ công gốc từ timesheet */
        BigDecimal originalHours,
        /** Giờ công sau điều chỉnh của kế toán */
        BigDecimal adjustedHours,
        /** Số giờ/ngày để quy đổi (mặc định 8.0) */
        BigDecimal hoursPerDay,
        /** Đơn vị tính: HOURLY | DAILY | MONTHLY — FE dùng để hiển thị giờ/ngày đúng */
        String priceUnit,
        BigDecimal unitSellPrice,
        BigDecimal originalAmount,
        BigDecimal adjustedAmount,
        String note,
        /**
         * Các đoạn thời gian NV phục vụ recipient (customer/vendor) của biên bản này trong tháng.
         * Một NV có thể có nhiều period (nếu chuyển dự án giữa tháng).
         * FE: render range, disable cell daily ngoài period.
         */
        List<StatementPeriodDto> periods,
        /**
         * Cờ cảnh báo: NV có log timesheet vào ngày KHÔNG thuộc period nào của recipient này.
         * FE: hiện cảnh báo vàng để kế toán review.
         */
        boolean hasOrphanTimesheet
) {}
