package com.hrms.domain.report.dto;

import java.math.BigDecimal;

public record RevenueReportItem(
        Long jobRoleId,
        String jobRoleName,
        long headcount,
        BigDecimal cost,
        BigDecimal revenue,
        BigDecimal margin
) {}
