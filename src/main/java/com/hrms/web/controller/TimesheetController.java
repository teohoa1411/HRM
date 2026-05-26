package com.hrms.web.controller;

import com.hrms.domain.timesheet.dto.*;
import com.hrms.domain.timesheet.service.TimesheetAttachmentService;
import com.hrms.domain.timesheet.service.TimesheetService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/timesheets")
@RequiredArgsConstructor
@Tag(name = "Timesheets", description = "Daily timesheet logging")
public class TimesheetController {

    private final TimesheetService service;
    private final TimesheetAttachmentService attachService;
    private final com.hrms.domain.account.repository.UserRepository userRepository;

    /** Validate that the target userId belongs to the current admin's company. */
    private void assertUserInCompany(Long userId, Long companyId) {
        if (!userRepository.existsByIdAndCompanyIdAndDeletedAtIsNull(userId, companyId)) {
            throw new com.hrms.domain.common.exception.TenantAccessDeniedException(
                    "User does not belong to your company");
        }
    }

    // â”€â”€ Entries (Employee) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Get own timesheet entries for a month (YYYY-MM)")
    public ResponseEntity<ApiResponse<List<TimesheetEntryResponse>>> getMonth(@RequestParam String month) {
        Long userId    = SecurityUtils.getCurrentUserId();
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(service.getMonth(userId, companyId, YearMonth.parse(month))));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Bulk upsert entries (hours=0 â†’ delete). NÃ©m lá»—i náº¿u thÃ¡ng Ä‘Ã£ khÃ³a hoáº·c entry bá»‹ khÃ³a bá»Ÿi leave.")
    public ResponseEntity<ApiResponse<List<TimesheetEntryResponse>>> bulkSave(
            @Valid @RequestBody TimesheetBulkRequest req) {
        Long userId    = SecurityUtils.getCurrentUserId();
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(
                service.bulkSave(req.entries(), userId, companyId, false), "Saved"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "XÃ³a 1 entry. NÃ©m lá»—i náº¿u thÃ¡ng Ä‘Ã£ khÃ³a hoáº·c entry bá»‹ khÃ³a bá»Ÿi leave.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id, SecurityUtils.getCurrentCompanyId(), false);
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }

    @DeleteMapping("/project")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "XÃ³a toÃ n bá»™ entries cá»§a 1 project trong 1 thÃ¡ng")
    public ResponseEntity<ApiResponse<String>> deleteProject(
            @RequestParam String projectKey,
            @RequestParam String month) {
        Long userId    = SecurityUtils.getCurrentUserId();
        Long companyId = SecurityUtils.getCurrentCompanyId();
        int count = service.deleteProject(userId, companyId, projectKey, YearMonth.parse(month), false);
        return ResponseEntity.ok(ApiResponse.ok("ÄÃ£ xÃ³a " + count + " báº£n ghi cá»§a project " + projectKey));
    }

    // â”€â”€ Monthly metadata (Employee) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/meta")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Get monthly description + custom field values + tráº¡ng thÃ¡i lock")
    public ResponseEntity<ApiResponse<TimesheetMonthlyResponse>> getMeta(@RequestParam String month) {
        Long userId    = SecurityUtils.getCurrentUserId();
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(service.getMeta(userId, companyId, month)));
    }

    @PutMapping("/meta")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Save monthly description + custom field values. NÃ©m lá»—i náº¿u thÃ¡ng Ä‘Ã£ khÃ³a.")
    public ResponseEntity<ApiResponse<TimesheetMonthlyResponse>> saveMeta(
            @RequestParam String month, @RequestBody TimesheetMonthlyRequest req) {
        Long userId    = SecurityUtils.getCurrentUserId();
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(service.saveMeta(userId, companyId, month, req, false)));
    }

    // â”€â”€ Attachments â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/attachments")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "List attachments for a month")
    public ResponseEntity<ApiResponse<List<TimesheetAttachmentResponse>>> listAttachments(
            @RequestParam String month) {
        return ResponseEntity.ok(ApiResponse.ok(
                attachService.list(SecurityUtils.getCurrentUserId(), month)));
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Upload PDF or image attachment for a month")
    public ResponseEntity<ApiResponse<TimesheetAttachmentResponse>> uploadAttachment(
            @RequestParam String month,
            @RequestParam("file") MultipartFile file) {
        Long userId    = SecurityUtils.getCurrentUserId();
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(attachService.upload(userId, companyId, month, file), "Uploaded"));
    }

    @GetMapping("/attachments/{id}/url")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Get presigned URL for attachment")
    public ResponseEntity<ApiResponse<String>> attachmentUrl(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        URL url = attachService.getUrl(id, SecurityUtils.getCurrentCompanyId(), userId);
        return ResponseEntity.ok(ApiResponse.ok(url.toString()));
    }

    @DeleteMapping("/attachments/{id}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        attachService.delete(id, SecurityUtils.getCurrentCompanyId(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }

    // â”€â”€ Admin: Timesheet entries â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/admin/overview")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get all employees timesheet summary for a month")
    public ResponseEntity<ApiResponse<List<EmployeeTimesheetSummary>>> adminOverview(
            @RequestParam String month) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(service.adminOverview(companyId, YearMonth.parse(month))));
    }

    @GetMapping("/admin/{userId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get specific user's timesheet entries (admin)")
    public ResponseEntity<ApiResponse<List<TimesheetEntryResponse>>> adminGetUser(
            @PathVariable Long userId, @RequestParam String month) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        assertUserInCompany(userId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(service.getMonth(userId, companyId, YearMonth.parse(month))));
    }

    @PostMapping("/admin/{userId}/bulk")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Bulk save for specific user (admin â€” bypass month lock vÃ  leave lock)")
    public ResponseEntity<ApiResponse<List<TimesheetEntryResponse>>> adminBulkSave(
            @PathVariable Long userId, @Valid @RequestBody TimesheetBulkRequest req) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        assertUserInCompany(userId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(
                service.bulkSave(req.entries(), userId, companyId, true), "Saved"));
    }

    @GetMapping("/admin/{userId}/meta")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<TimesheetMonthlyResponse>> adminGetMeta(
            @PathVariable Long userId, @RequestParam String month) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        assertUserInCompany(userId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(service.getMeta(userId, companyId, month)));
    }

    @PutMapping("/admin/{userId}/meta")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Admin save monthly meta (bypass month lock)")
    public ResponseEntity<ApiResponse<TimesheetMonthlyResponse>> adminSaveMeta(
            @PathVariable Long userId, @RequestParam String month,
            @RequestBody TimesheetMonthlyRequest req) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        assertUserInCompany(userId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(service.saveMeta(userId, companyId, month, req, true)));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Admin xÃ³a 1 entry (bypass month lock vÃ  leave lock)")
    public ResponseEntity<ApiResponse<Void>> adminDelete(@PathVariable Long id) {
        service.delete(id, SecurityUtils.getCurrentCompanyId(), true);
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }

    // Admin: Attachments

    @GetMapping("/admin/{userId}/attachments")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<TimesheetAttachmentResponse>>> adminListAttachments(
            @PathVariable Long userId, @RequestParam String month) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        assertUserInCompany(userId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(attachService.list(userId, month)));
    }

    @PostMapping(value = "/admin/{userId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<TimesheetAttachmentResponse>> adminUploadAttachment(
            @PathVariable Long userId, @RequestParam String month,
            @RequestParam("file") MultipartFile file) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        assertUserInCompany(userId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(attachService.upload(userId, companyId, month, file)));
    }

    @GetMapping("/admin/attachments/{id}/url")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> adminAttachmentUrl(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        URL url = attachService.getUrlAdmin(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(url.toString()));
    }

    @DeleteMapping("/admin/attachments/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> adminDeleteAttachment(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        attachService.deleteAdmin(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }


    // â”€â”€ Lock Status (multi-level) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/{yearMonth}/lock-status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kiá»ƒm tra táº¥t cáº£ cÃ¡c táº§ng khÃ³a cá»§a thÃ¡ng cho user hiá»‡n táº¡i (Period/Payroll/Statement)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> lockStatusAllLevels(@PathVariable String yearMonth) {
        Long userId    = SecurityUtils.getCurrentUserId();
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(service.getLockStatusAllLevels(companyId, userId, yearMonth)));
    }

    // â”€â”€ Admin: Lock / Unlock thÃ¡ng â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/admin/lock")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Láº¥y tráº¡ng thÃ¡i khÃ³a timesheet cá»§a thÃ¡ng (YYYY-MM)")
    public ResponseEntity<ApiResponse<TimesheetLockResponse>> getLockStatus(@RequestParam String month) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(service.getLockStatus(companyId, month)));
    }

    @PostMapping("/admin/lock")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "KhÃ³a timesheet thÃ¡ng (YYYY-MM). NhÃ¢n sá»± sáº½ khÃ´ng thá»ƒ sá»­a sau khi khÃ³a.")
    public ResponseEntity<ApiResponse<TimesheetLockResponse>> lockMonth(@RequestParam String month) {
        Long companyId  = SecurityUtils.getCurrentCompanyId();
        Long adminUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                service.lockMonth(companyId, month, adminUserId),
                "Timesheet thÃ¡ng " + month + " Ä‘Ã£ Ä‘Æ°á»£c khÃ³a thÃ nh cÃ´ng."));
    }

    @DeleteMapping("/admin/lock")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Má»Ÿ khÃ³a timesheet thÃ¡ng (YYYY-MM). NhÃ¢n sá»± cÃ³ thá»ƒ chá»‰nh sá»­a láº¡i.")
    public ResponseEntity<ApiResponse<TimesheetLockResponse>> unlockMonth(@RequestParam String month) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(
                service.unlockMonth(companyId, month),
                "Timesheet thÃ¡ng " + month + " Ä‘Ã£ Ä‘Æ°á»£c má»Ÿ khÃ³a."));
    }

    // â”€â”€ Accountant: Confirm / Revert timesheet (bÆ°á»›c 2 sau HR lock) â”€â”€â”€â”€â”€â”€

    @PostMapping("/admin/accountant-confirm")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Káº¿ toÃ¡n xÃ¡c nháº­n timesheet thÃ¡ng (YYYY-MM). " +
            "Sau khi xÃ¡c nháº­n: khÃ´ng ai sá»­a Ä‘Æ°á»£c timesheet; cho phÃ©p táº¡o BiÃªn báº£n KH/Vendor.")
    public ResponseEntity<ApiResponse<TimesheetLockResponse>> accountantConfirm(@RequestParam String month) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                service.confirmByAccountant(companyId, month, userId),
                "Timesheet thÃ¡ng " + month + " Ä‘Ã£ Ä‘Æ°á»£c Káº¿ toÃ¡n xÃ¡c nháº­n."));
    }

    @PostMapping("/admin/accountant-revert-confirm")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Káº¿ toÃ¡n há»§y xÃ¡c nháº­n timesheet (revert). Body: { reason: string (>=20 kÃ½ tá»±) }. " +
            "Cháº·n náº¿u cÃ²n biÃªn báº£n SENT/CONFIRMED trong thÃ¡ng Ä‘Ã³.")
    public ResponseEntity<ApiResponse<TimesheetLockResponse>> accountantRevertConfirm(
            @RequestParam String month,
            @RequestBody java.util.Map<String, String> body) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(ApiResponse.ok(
                service.revertAccountantConfirm(companyId, month, reason, userId),
                "ÄÃ£ há»§y xÃ¡c nháº­n timesheet thÃ¡ng " + month + ". Báº¡n cÃ³ thá»ƒ chá»‰nh sá»­a láº¡i."));
    }
}
