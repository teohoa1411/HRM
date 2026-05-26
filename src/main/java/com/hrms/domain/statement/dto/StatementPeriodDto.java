package com.hrms.domain.statement.dto;

import java.time.LocalDate;

/**
 * Một đoạn thời gian (period) mà nhân sự được phân công cho customer/vendor cụ thể trong tháng.
 * FE dùng để hiển thị "Phân công: 15/04 - 30/04" và disable cell daily ngoài period.
 */
public record StatementPeriodDto(
        LocalDate startDate,
        LocalDate endDate,
        Long customerId,
        Long vendorId
) {}
