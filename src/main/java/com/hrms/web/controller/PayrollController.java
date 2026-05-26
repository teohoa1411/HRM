package com.hrms.web.controller;

import com.hrms.domain.payroll.dto.PayrollAdjustmentDto;
import com.hrms.domain.payroll.dto.PayrollRecordDto;
import com.hrms.domain.payroll.dto.UpdatePayrollRequest;
import com.hrms.domain.payroll.entity.PayrollRecordHistory;
import com.hrms.domain.payroll.repository.PayrollRecordHistoryRepository;
import com.hrms.domain.payroll.repository.PayrollRecordRepository;
import com.hrms.domain.payroll.service.PayrollService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/payroll")
@RequiredArgsConstructor
@Tag(name = "Payroll", description = "Báº£ng lÆ°Æ¡ng ná»™i bá»™")
public class PayrollController {

    private final PayrollService payrollService;
    private final PayrollRecordHistoryRepository historyRepository;

    @PostMapping("/generate/{yearMonth}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Sinh PayrollRecord tá»« BillingRecord cho thÃ¡ng chá»‰ Ä‘á»‹nh")
    public ResponseEntity<ApiResponse<List<PayrollRecordDto>>> generate(@PathVariable String yearMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(payrollService.generatePayroll(companyId, yearMonth)));
    }

    @GetMapping("/{yearMonth}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Láº¥y danh sÃ¡ch PayrollRecord thÃ¡ng")
    public ResponseEntity<ApiResponse<List<PayrollRecordDto>>> getPayroll(@PathVariable String yearMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(payrollService.getPayroll(companyId, yearMonth)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Cáº­p nháº­t bonus, deduction, ghi chÃº")
    public ResponseEntity<ApiResponse<PayrollRecordDto>> update(
            @PathVariable Long id, @RequestBody UpdatePayrollRequest req) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(payrollService.updatePayrollRecord(companyId, id, req)));
    }

    @PostMapping("/{yearMonth}/confirm")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "XÃ¡c nháº­n toÃ n bá»™ báº£ng lÆ°Æ¡ng thÃ¡ng")
    public ResponseEntity<ApiResponse<Void>> confirm(@PathVariable String yearMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        payrollService.confirmPayroll(companyId, yearMonth, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Báº£ng lÆ°Æ¡ng Ä‘Ã£ Ä‘Æ°á»£c xÃ¡c nháº­n"));
    }

    @GetMapping("/{yearMonth}/export")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Xuáº¥t file Excel báº£ng lÆ°Æ¡ng")
    public ResponseEntity<byte[]> exportExcel(@PathVariable String yearMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        byte[] data = payrollService.exportPayrollExcel(companyId, yearMonth);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"payroll_" + yearMonth + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping("/{yearMonth}/reset")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Reset báº£ng lÆ°Æ¡ng vá» DRAFT, lÆ°u lá»‹ch sá»­")
    public ResponseEntity<ApiResponse<Void>> reset(
            @PathVariable String yearMonth,
            @RequestBody Map<String, String> body) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        payrollService.resetPayroll(companyId, yearMonth, body.get("reason"), SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(null, "ÄÃ£ reset báº£ng lÆ°Æ¡ng"));
    }

    @PostMapping("/{yearMonth}/mark-paid")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Mark payroll as PAID (locks permanently)")
    public ResponseEntity<ApiResponse<Void>> markPaid(@PathVariable String yearMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();
        payrollService.markPaid(companyId, yearMonth, userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Payroll marked as PAID"));
    }

    @GetMapping("/{yearMonth}/history")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Xem lá»‹ch sá»­ thay Ä‘á»•i báº£ng lÆ°Æ¡ng")
    public ResponseEntity<ApiResponse<List<PayrollRecordHistory>>> history(@PathVariable String yearMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        // Láº¥y táº¥t cáº£ history records cho thÃ¡ng (qua payroll_record_id)
        List<Long> ids = payrollService.getPayroll(companyId, yearMonth)
                .stream().map(PayrollRecordDto::id).toList();
        List<PayrollRecordHistory> histories = ids.stream()
                .flatMap(id -> historyRepository.findByPayrollRecordIdOrderByChangedAtDesc(id).stream())
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(histories));
    }

    @GetMapping("/{yearMonth}/stale-check")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Kiá»ƒm tra xem cÃ³ PayrollRecord STALE khÃ´ng")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> staleCheck(@PathVariable String yearMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        boolean hasStale = payrollService.hasStaleRecords(companyId, yearMonth);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("hasStale", hasStale)));
    }

    // ==================== ADJUSTMENT CRUD ====================

    @GetMapping("/{payrollRecordId}/adjustments")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Láº¥y danh sÃ¡ch khoáº£n phÃ¡t sinh cá»§a payroll record")
    public ResponseEntity<ApiResponse<List<PayrollAdjustmentDto>>> getAdjustments(@PathVariable Long payrollRecordId) {
        return ResponseEntity.ok(ApiResponse.ok(payrollService.getAdjustments(payrollRecordId)));
    }

    @PostMapping("/{payrollRecordId}/adjustments")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "ThÃªm khoáº£n phÃ¡t sinh (BONUS/DEDUCTION/ALLOWANCE)")
    public ResponseEntity<ApiResponse<PayrollAdjustmentDto>> addAdjustment(
            @PathVariable Long payrollRecordId,
            @RequestBody PayrollAdjustmentDto dto) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(payrollService.addAdjustment(companyId, payrollRecordId, dto)));
    }

    @DeleteMapping("/{payrollRecordId}/adjustments/{adjustmentId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "XÃ³a khoáº£n phÃ¡t sinh")
    public ResponseEntity<ApiResponse<Void>> removeAdjustment(
            @PathVariable Long payrollRecordId,
            @PathVariable Long adjustmentId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        payrollService.removeAdjustment(companyId, payrollRecordId, adjustmentId);
        return ResponseEntity.ok(ApiResponse.ok(null, "ÄÃ£ xÃ³a khoáº£n phÃ¡t sinh"));
    }
}
