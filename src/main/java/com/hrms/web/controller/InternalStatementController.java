package com.hrms.web.controller;

import com.hrms.domain.internal_statement.dto.*;
import com.hrms.domain.internal_statement.service.InternalStatementService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/internal-statements")
@RequiredArgsConstructor
@Tag(name = "Internal Statements", description = "BiÃªn báº£n ná»™i bá»™ - HR Ä‘iá»u chá»‰nh giá» cÃ´ng")
public class InternalStatementController {

    private final InternalStatementService service;
    private final com.hrms.domain.employee.service.EmployeeService employeeService;

    /** Tá»•ng há»£p/lÃ m má»›i biÃªn báº£n ná»™i bá»™ cho toÃ n bá»™ nhÃ¢n sá»± trong thÃ¡ng */
    @PostMapping("/generate/{yearMonth}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Tá»•ng há»£p biÃªn báº£n ná»™i bá»™ tá»« timesheet + nghá»‰ phÃ©p")
    public ResponseEntity<ApiResponse<Void>> generate(@PathVariable String yearMonth) {
        service.generateForMonth(SecurityUtils.getCurrentCompanyId(), yearMonth);
        return ResponseEntity.ok(ApiResponse.ok(null, "ÄÃ£ tá»•ng há»£p biÃªn báº£n ná»™i bá»™"));
    }

    /** Tá»•ng há»£p/lÃ m má»›i cho 1 nhÃ¢n sá»± */
    @PostMapping("/{publicId}/generate/{yearMonth}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Tá»•ng há»£p biÃªn báº£n ná»™i bá»™ cho 1 nhÃ¢n sá»±")
    public ResponseEntity<ApiResponse<Void>> generateForEmployee(
            @PathVariable String publicId, @PathVariable String yearMonth) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        service.generateForEmployee(companyId, employeeId, yearMonth);
        return ResponseEntity.ok(ApiResponse.ok(null, "ÄÃ£ tá»•ng há»£p biÃªn báº£n ná»™i bá»™"));
    }

    /** Tá»•ng quan: danh sÃ¡ch táº¥t cáº£ nhÃ¢n sá»± + tÃ³m táº¯t */
    @GetMapping("/{yearMonth}/overview")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Tá»•ng quan biÃªn báº£n ná»™i bá»™ thÃ¡ng")
    public ResponseEntity<ApiResponse<List<InternalOverviewItemDto>>> overview(@PathVariable String yearMonth) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.getOverview(SecurityUtils.getCurrentCompanyId(), yearMonth)));
    }

    /** Calendar per nhÃ¢n sá»± - toÃ n bá»™ ngÃ y trong thÃ¡ng */
    @GetMapping("/{yearMonth}/employee/{publicId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Láº¥y chi tiáº¿t calendar cá»§a 1 nhÃ¢n sá»±")
    public ResponseEntity<ApiResponse<InternalEmployeeCalendarDto>> getEmployeeCalendar(
            @PathVariable String yearMonth, @PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(
                service.getEmployeeCalendar(companyId, employeeId, yearMonth)));
    }

    /** HR chá»‰nh sá»­a 1 ngÃ y cá»¥ thá»ƒ cá»§a nhÃ¢n sá»± */
    @PutMapping("/{yearMonth}/employee/{publicId}/day/{date}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Chá»‰nh sá»­a giá» cÃ´ng 1 ngÃ y")
    public ResponseEntity<ApiResponse<InternalCalendarDayDto>> updateDay(
            @PathVariable String yearMonth,
            @PathVariable String publicId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody UpdateInternalDayRequest req) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                service.updateDay(companyId, employeeId, yearMonth, date, req, userId)));
    }

    /** Confirm biÃªn báº£n cá»§a 1 nhÃ¢n sá»± */
    @PostMapping("/{yearMonth}/employee/{publicId}/confirm")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','ACCOUNTANT')")
    @Operation(summary = "XÃ¡c nháº­n biÃªn báº£n ná»™i bá»™ cá»§a 1 nhÃ¢n sá»±")
    public ResponseEntity<ApiResponse<Void>> confirmEmployee(
            @PathVariable String yearMonth, @PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        service.confirmEmployee(companyId, employeeId, yearMonth,
                SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(null, "ÄÃ£ xÃ¡c nháº­n"));
    }

    /** Confirm táº¥t cáº£ */
    @PostMapping("/{yearMonth}/confirm-all")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','ACCOUNTANT')")
    @Operation(summary = "XÃ¡c nháº­n biÃªn báº£n ná»™i bá»™ táº¥t cáº£ nhÃ¢n sá»±")
    public ResponseEntity<ApiResponse<Void>> confirmAll(@PathVariable String yearMonth) {
        service.confirmAll(SecurityUtils.getCurrentCompanyId(), yearMonth, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(null, "ÄÃ£ xÃ¡c nháº­n táº¥t cáº£"));
    }

    /** Unconfirm (má»Ÿ láº¡i DRAFT) */
    @PostMapping("/{yearMonth}/employee/{publicId}/unconfirm")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','ACCOUNTANT')")
    @Operation(summary = "Há»§y xÃ¡c nháº­n (vá» DRAFT)")
    public ResponseEntity<ApiResponse<Void>> unconfirmEmployee(
            @PathVariable String yearMonth, @PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        service.unconfirmEmployee(companyId, employeeId, yearMonth);
        return ResponseEntity.ok(ApiResponse.ok(null, "ÄÃ£ há»§y xÃ¡c nháº­n"));
    }

    /** Export Excel toÃ n bá»™ thÃ¡ng */
    @PostMapping("/{yearMonth}/employee/{publicId}/reopen")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<Void>> reopenEmployee(
            @PathVariable String yearMonth, @PathVariable String publicId,
            @RequestBody ReopenInternalStatementRequest req) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        service.reopenEmployee(companyId, employeeId, yearMonth, req.reason());
        return ResponseEntity.ok(ApiResponse.ok(null, "Reopened"));
    }

    @GetMapping("/{yearMonth}/export")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Xuáº¥t Excel biÃªn báº£n ná»™i bá»™")
    public ResponseEntity<byte[]> exportExcel(@PathVariable String yearMonth) {
        byte[] data = service.exportExcel(SecurityUtils.getCurrentCompanyId(), yearMonth);
        String filename = "BienBanNoiBo_" + yearMonth + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping("/{yearMonth}/close")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "ÄÃ³ng biÃªn báº£n ná»™i bá»™ thÃ¡ng â€” khÃ³a timesheet")
    public ResponseEntity<ApiResponse<Void>> closeMonth(
            @PathVariable String yearMonth,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        service.closeMonth(SecurityUtils.getCurrentCompanyId(), yearMonth,
                SecurityUtils.getCurrentUserId(), reason);
        return ResponseEntity.ok(ApiResponse.ok(null, "ÄÃ£ Ä‘Ã³ng biÃªn báº£n ná»™i bá»™ thÃ¡ng " + yearMonth));
    }

    @PostMapping("/{yearMonth}/reopen")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Má»Ÿ láº¡i biÃªn báº£n ná»™i bá»™ â€” chá»‰ COMPANY_ADMIN")
    public ResponseEntity<ApiResponse<Void>> reopenMonth(
            @PathVariable String yearMonth,
            @RequestBody java.util.Map<String, String> body) {
        String reason = body.get("reason");
        service.reopenMonth(SecurityUtils.getCurrentCompanyId(), yearMonth,
                SecurityUtils.getCurrentUserId(), reason);
        return ResponseEntity.ok(ApiResponse.ok(null, "ÄÃ£ má»Ÿ láº¡i biÃªn báº£n ná»™i bá»™ thÃ¡ng " + yearMonth));
    }
}
