package com.hrms.domain.payroll.dto;

import java.util.List;

public record PayrollReadinessDto(
        boolean canGenerate, int eligibleEmployees, int confirmedEmployees,
        List<PayrollReadinessEmployeeDto> missingStatementEmployees,
        List<PayrollReadinessEmployeeDto> unconfirmedEmployees,
        List<PayrollReadinessEmployeeDto> missingSalaryEmployees,
        List<PayrollReadinessEmployeeDto> staleEmployees,
        List<String> blockingReasons
) {}
