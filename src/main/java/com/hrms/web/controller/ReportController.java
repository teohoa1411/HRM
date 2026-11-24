package com.hrms.web.controller;

import com.hrms.domain.report.dto.CostReportResponse;
import com.hrms.domain.report.dto.RevenueReportItem;
import com.hrms.domain.report.service.ReportService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Financial and HR reports")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/costs")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Cost summary report for a month range (YYYY-MM)")
    public ResponseEntity<ApiResponse<CostReportResponse>> getCostReport(
            @RequestParam String fromMonth,
            @RequestParam String toMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(reportService.getCostReport(companyId, fromMonth, toMonth)));
    }

    @GetMapping("/revenue/by-job-role")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Revenue breakdown by job role")
    public ResponseEntity<ApiResponse<List<RevenueReportItem>>> getRevenueByJobRole(
            @RequestParam String fromMonth,
            @RequestParam String toMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(reportService.getRevenueByJobRole(companyId, fromMonth, toMonth)));
    }

    @GetMapping("/revenue/by-source-type")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Revenue breakdown by source type (INTERNAL vs VENDOR)")
    public ResponseEntity<ApiResponse<List<RevenueReportItem>>> getRevenueBySourceType(
            @RequestParam String fromMonth,
            @RequestParam String toMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(reportService.getRevenueBySourceType(companyId, fromMonth, toMonth)));
    }

    @GetMapping("/revenue/by-department")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Revenue breakdown by department")
    public ResponseEntity<ApiResponse<List<RevenueReportItem>>> getRevenueByDepartment(
            @RequestParam String fromMonth,
            @RequestParam String toMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(reportService.getRevenueByDepartment(companyId, fromMonth, toMonth)));
    }

    @GetMapping("/revenue/by-customer")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Revenue breakdown by customer")
    public ResponseEntity<ApiResponse<List<RevenueReportItem>>> getRevenueByCustomer(
            @RequestParam String fromMonth,
            @RequestParam String toMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(reportService.getRevenueByCustomer(companyId, fromMonth, toMonth)));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Dashboard summary: active employee count")
    public ResponseEntity<ApiResponse<Long>> getDashboardStats() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(reportService.countActiveEmployees(companyId)));
    }
}
