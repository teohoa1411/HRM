package com.hrms.web.controller;

import com.hrms.domain.finance.dto.EmployeeFinancialDto;
import com.hrms.domain.finance.dto.MonthlySummaryDto;
import com.hrms.domain.finance.entity.MonthlyFinancialRecord;
import com.hrms.domain.finance.repository.MonthlyFinancialRecordRepository;
import com.hrms.domain.finance.service.FinancialReportService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Financial Reports", description = "BÃ¡o cÃ¡o tÃ i chÃ­nh thÃ¡ng")
public class FinancialReportController {

    private final FinancialReportService reportService;
    private final MonthlyFinancialRecordRepository recordRepository;
    private final com.hrms.domain.vendor.repository.VendorRepository vendorRepository;

    /**
     * Tá»•ng há»£p láº¡i bÃ¡o cÃ¡o thá»§ cÃ´ng (force recalculate).
     * Tá»± Ä‘á»™ng gá»i sau billing/calculate vÃ  payroll/confirm.
     */
    @PostMapping("/generate/{yearMonth}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Tá»•ng há»£p láº¡i bÃ¡o cÃ¡o tÃ i chÃ­nh (force refresh)")
    public ResponseEntity<ApiResponse<Void>> generate(@PathVariable String yearMonth) {
        reportService.generateMonthlyFinancialRecords(SecurityUtils.getCurrentCompanyId(), yearMonth);
        return ResponseEntity.ok(ApiResponse.ok(null, "ÄÃ£ cáº­p nháº­t bÃ¡o cÃ¡o tÃ i chÃ­nh"));
    }

    /**
     * Tá»•ng quan tÃ i chÃ­nh thÃ¡ng â€” Ä‘á»c tháº³ng tá»« DB, khÃ´ng tá»± tÃ­nh láº¡i.
     * Dá»¯ liá»‡u Ä‘Æ°á»£c chuáº©n bá»‹ sáºµn sau billing/calculate hoáº·c payroll/confirm.
     */
    @GetMapping("/monthly-summary")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Tá»•ng quan tÃ i chÃ­nh thÃ¡ng")
    public ResponseEntity<ApiResponse<MonthlySummaryDto>> monthlySummary(
            @RequestParam int month, @RequestParam int year) {
        String yearMonth = String.format("%04d-%02d", year, month);
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(reportService.getMonthlySummary(companyId, yearMonth)));
    }

    /**
     * BÃ¡o cÃ¡o per nhÃ¢n sá»± â€” Ä‘á»c tháº³ng tá»« DB.
     */
    @GetMapping("/per-employee")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "BÃ¡o cÃ¡o tÃ i chÃ­nh per nhÃ¢n sá»±")
    public ResponseEntity<ApiResponse<List<EmployeeFinancialDto>>> perEmployee(
            @RequestParam int month, @RequestParam int year) {
        String yearMonth = String.format("%04d-%02d", year, month);
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(reportService.getPerEmployeeReport(companyId, yearMonth)));
    }

    @GetMapping("/per-customer")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Tá»•ng tiá»n tá»«ng khÃ¡ch hÃ ng pháº£i tráº£")
    public ResponseEntity<ApiResponse<List<MonthlyFinancialRecord>>> perCustomer(
            @RequestParam int month, @RequestParam int year) {
        String yearMonth = String.format("%04d-%02d", year, month);
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(
                recordRepository.findByCompanyIdAndRecordMonth(companyId, yearMonth)));
    }

    @GetMapping("/per-vendor")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Tá»•ng tiá»n pháº£i tráº£ tá»«ng vendor")
    public ResponseEntity<ApiResponse<List<MonthlyFinancialRecord>>> perVendor(
            @RequestParam int month, @RequestParam int year,
            @RequestParam Long vendorId) {
        String yearMonth = String.format("%04d-%02d", year, month);
        Long companyId = SecurityUtils.getCurrentCompanyId();
        // Validate vendorId belongs to current company
        vendorRepository.findByIdAndCompanyIdAndDeletedAtIsNull(vendorId, companyId)
                .orElseThrow(() -> new com.hrms.domain.common.exception.NotFoundException("Vendor", vendorId));
        return ResponseEntity.ok(ApiResponse.ok(
                recordRepository.findByCompanyIdAndRecordMonthAndVendorId(companyId, yearMonth, vendorId)));
    }
}
