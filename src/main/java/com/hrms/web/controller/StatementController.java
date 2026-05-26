package com.hrms.web.controller;

import com.hrms.domain.statement.dto.StatementDailyDto;
import com.hrms.domain.statement.dto.StatementDto;
import com.hrms.domain.statement.dto.StatementHistoryDto;
import com.hrms.domain.statement.dto.StatementLineItemDto;
import com.hrms.domain.statement.dto.UpdateDailyEntryRequest;
import com.hrms.domain.statement.dto.UpdateLineItemRequest;
import com.hrms.domain.statement.service.StatementService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/statements")
@RequiredArgsConstructor
@Tag(name = "Statements", description = "BiÃªn báº£n xÃ¡c nháº­n cÃ´ng KH/Vendor")
public class StatementController {

    private final StatementService statementService;

    // â”€â”€ CUSTOMER LIST & DETAIL â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/customer/{yearMonth}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<List<StatementDto>>> listCustomer(@PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.listCustomerStatements(SecurityUtils.getCurrentCompanyId(), yearMonth)));
    }

    @GetMapping("/customer/{customerId}/{yearMonth}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementDto>> getCustomer(
            @PathVariable Long customerId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.getCustomerStatement(SecurityUtils.getCurrentCompanyId(), customerId, yearMonth)));
    }

    @PostMapping("/customer/{customerId}/{yearMonth}/generate")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementDto>> generateCustomer(
            @PathVariable Long customerId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.generateCustomerStatement(SecurityUtils.getCurrentCompanyId(), customerId, yearMonth)));
    }

    @PutMapping("/customer/{customerId}/{yearMonth}/line-items/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementLineItemDto>> updateLineItem(
            @PathVariable Long customerId, @PathVariable String yearMonth,
            @PathVariable Long id, @RequestBody UpdateLineItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.updateLineItem(SecurityUtils.getCurrentCompanyId(), id, req)));
    }

    @GetMapping("/customer/{customerId}/{yearMonth}/export")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<byte[]> exportCustomer(
            @PathVariable Long customerId, @PathVariable String yearMonth) {
        byte[] data = statementService.exportCustomerStatementExcel(
                SecurityUtils.getCurrentCompanyId(), customerId, yearMonth,
                SecurityUtils.getCurrentUserId());
        String filename = "bienban_kh_" + customerId + "_" + yearMonth + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Statement-Status", "EXPORTED")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping("/customer/{customerId}/{yearMonth}/mark-sent")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementDto>> markCustomerSent(
            @PathVariable Long customerId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.markAsSent(SecurityUtils.getCurrentCompanyId(),
                        "CUSTOMER", customerId, yearMonth, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/customer/{customerId}/{yearMonth}/mark-exported")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementDto>> markCustomerExported(
            @PathVariable Long customerId, @PathVariable String yearMonth,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String s3Key = body != null ? body.get("s3Key") : null;
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.markAsExported(SecurityUtils.getCurrentCompanyId(),
                        "CUSTOMER", customerId, yearMonth, SecurityUtils.getCurrentUserId(), s3Key)));
    }

    @PostMapping("/customer/{customerId}/{yearMonth}/mark-confirmed")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementDto>> markCustomerConfirmed(
            @PathVariable Long customerId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.markAsConfirmed(SecurityUtils.getCurrentCompanyId(),
                        "CUSTOMER", customerId, yearMonth, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/customer/{customerId}/{yearMonth}/reset-draft")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementDto>> resetCustomerDraft(
            @PathVariable Long customerId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.resetToDraft(SecurityUtils.getCurrentCompanyId(),
                        "CUSTOMER", customerId, yearMonth, SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/customer/{customerId}/{yearMonth}/history")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<List<StatementHistoryDto>>> getCustomerHistory(
            @PathVariable Long customerId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.getHistory(SecurityUtils.getCurrentCompanyId(),
                        "CUSTOMER", customerId, yearMonth)));
    }

    @PostMapping("/customer/{customerId}/{yearMonth}/close")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "ÄÃ³ng biÃªn báº£n KH â€” khÃ³a timesheet")
    public ResponseEntity<ApiResponse<StatementDto>> closeCustomer(
            @PathVariable Long customerId, @PathVariable String yearMonth,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.closeStatement(SecurityUtils.getCurrentCompanyId(),
                        "CUSTOMER", customerId, yearMonth,
                        SecurityUtils.getCurrentUserId(), reason)));
    }

    @PostMapping("/customer/{customerId}/{yearMonth}/reopen")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Má»Ÿ láº¡i biÃªn báº£n KH â€” chá»‰ COMPANY_ADMIN")
    public ResponseEntity<ApiResponse<StatementDto>> reopenCustomer(
            @PathVariable Long customerId, @PathVariable String yearMonth,
            @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.reopenStatement(SecurityUtils.getCurrentCompanyId(),
                        "CUSTOMER", customerId, yearMonth,
                        SecurityUtils.getCurrentUserId(), reason)));
    }

    // â”€â”€ CUSTOMER DAILY â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/line-items/{lineItemId}/daily/{yearMonth}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Láº¥y lá»‹ch cháº¥m cÃ´ng theo ngÃ y cá»§a má»™t line item")
    public ResponseEntity<ApiResponse<List<StatementDailyDto>>> getLineItemDailyCalendar(
            @PathVariable Long lineItemId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.getLineItemDailyCalendar(
                        SecurityUtils.getCurrentCompanyId(), lineItemId, yearMonth)));
    }

    @PostMapping("/line-items/{lineItemId}/daily/{yearMonth}/generate")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Táº¡o láº¡i dá»¯ liá»‡u theo ngÃ y tá»« biÃªn báº£n ná»™i bá»™")
    public ResponseEntity<ApiResponse<List<StatementDailyDto>>> generateLineItemDaily(
            @PathVariable Long lineItemId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.generateLineItemDailyEntries(
                        SecurityUtils.getCurrentCompanyId(), lineItemId, yearMonth)));
    }

    @PutMapping("/line-items/{lineItemId}/daily/{date}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Cáº­p nháº­t giá»/ghi chÃº cho má»™t ngÃ y cá»¥ thá»ƒ")
    public ResponseEntity<ApiResponse<StatementDailyDto>> updateDailyEntry(
            @PathVariable Long lineItemId, @PathVariable String date,
            @RequestBody UpdateDailyEntryRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.updateLineItemDailyEntry(
                        SecurityUtils.getCurrentCompanyId(), lineItemId, LocalDate.parse(date), req)));
    }

    // â”€â”€ VENDOR LIST & DETAIL â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/vendor/{yearMonth}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<List<StatementDto>>> listVendor(@PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.listVendorStatements(SecurityUtils.getCurrentCompanyId(), yearMonth)));
    }

    @GetMapping("/vendor/{vendorId}/{yearMonth}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementDto>> getVendor(
            @PathVariable Long vendorId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.getVendorStatement(SecurityUtils.getCurrentCompanyId(), vendorId, yearMonth)));
    }

    @PostMapping("/vendor/{vendorId}/{yearMonth}/generate")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementDto>> generateVendor(
            @PathVariable Long vendorId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.generateVendorStatement(SecurityUtils.getCurrentCompanyId(), vendorId, yearMonth)));
    }

    @PutMapping("/vendor/{vendorId}/{yearMonth}/line-items/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementLineItemDto>> updateVendorLineItem(
            @PathVariable Long vendorId, @PathVariable String yearMonth,
            @PathVariable Long id, @RequestBody UpdateLineItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.updateLineItem(SecurityUtils.getCurrentCompanyId(), id, req)));
    }

    @GetMapping("/vendor/{vendorId}/{yearMonth}/export")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<byte[]> exportVendor(
            @PathVariable Long vendorId, @PathVariable String yearMonth) {
        byte[] data = statementService.exportVendorStatementExcel(
                SecurityUtils.getCurrentCompanyId(), vendorId, yearMonth,
                SecurityUtils.getCurrentUserId());
        String filename = "bienban_vendor_" + vendorId + "_" + yearMonth + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Statement-Status", "EXPORTED")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping("/vendor/{vendorId}/{yearMonth}/mark-sent")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementDto>> markVendorSent(
            @PathVariable Long vendorId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.markAsSent(SecurityUtils.getCurrentCompanyId(),
                        "VENDOR", vendorId, yearMonth, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/vendor/{vendorId}/{yearMonth}/mark-exported")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementDto>> markVendorExported(
            @PathVariable Long vendorId, @PathVariable String yearMonth,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String s3Key = body != null ? body.get("s3Key") : null;
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.markAsExported(SecurityUtils.getCurrentCompanyId(),
                        "VENDOR", vendorId, yearMonth, SecurityUtils.getCurrentUserId(), s3Key)));
    }

    @PostMapping("/vendor/{vendorId}/{yearMonth}/mark-confirmed")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementDto>> markVendorConfirmed(
            @PathVariable Long vendorId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.markAsConfirmed(SecurityUtils.getCurrentCompanyId(),
                        "VENDOR", vendorId, yearMonth, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/vendor/{vendorId}/{yearMonth}/reset-draft")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StatementDto>> resetVendorDraft(
            @PathVariable Long vendorId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.resetToDraft(SecurityUtils.getCurrentCompanyId(),
                        "VENDOR", vendorId, yearMonth, SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/vendor/{vendorId}/{yearMonth}/history")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<List<StatementHistoryDto>>> getVendorHistory(
            @PathVariable Long vendorId, @PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.getHistory(SecurityUtils.getCurrentCompanyId(),
                        "VENDOR", vendorId, yearMonth)));
    }

    @PostMapping("/vendor/{vendorId}/{yearMonth}/close")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "ÄÃ³ng biÃªn báº£n Vendor â€” khÃ³a timesheet")
    public ResponseEntity<ApiResponse<StatementDto>> closeVendor(
            @PathVariable Long vendorId, @PathVariable String yearMonth,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.closeStatement(SecurityUtils.getCurrentCompanyId(),
                        "VENDOR", vendorId, yearMonth,
                        SecurityUtils.getCurrentUserId(), reason)));
    }

    @PostMapping("/vendor/{vendorId}/{yearMonth}/reopen")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Má»Ÿ láº¡i biÃªn báº£n Vendor â€” chá»‰ COMPANY_ADMIN")
    public ResponseEntity<ApiResponse<StatementDto>> reopenVendor(
            @PathVariable Long vendorId, @PathVariable String yearMonth,
            @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.reopenStatement(SecurityUtils.getCurrentCompanyId(),
                        "VENDOR", vendorId, yearMonth,
                        SecurityUtils.getCurrentUserId(), reason)));
    }
}
