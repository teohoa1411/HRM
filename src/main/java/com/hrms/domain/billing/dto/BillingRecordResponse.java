package com.hrms.domain.billing.dto;

import com.hrms.domain.common.enums.EmployeeSourceType;
import com.hrms.domain.common.enums.PriceUnit;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillingRecordResponse(
        Long id,
        Long companyId,
        Long employeeId,
        String employeeName,
        String employeeCode,
        Long jobRoleId,
        String jobRoleName,
        String billingMonth,
        /** Đầu period — sau V29 mỗi NV/tháng có thể có nhiều record nếu chuyển dự án giữa tháng. */
        LocalDate periodStart,
        LocalDate periodEnd,
        EmployeeSourceType sourceType,
        Long vendorId,
        String vendorName,
        Long customerId,
        String customerName,
        BigDecimal daysWorked,
        BigDecimal daysOff,
        double totalWorkdays,
        PriceUnit priceUnit,
        BigDecimal unitCostPrice,
        BigDecimal unitSellPrice,
        BigDecimal costAmount,
        BigDecimal sellAmount,
        String currency,
        String calcStatus
) {}
