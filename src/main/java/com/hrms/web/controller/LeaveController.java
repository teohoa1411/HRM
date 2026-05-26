package com.hrms.web.controller;

import com.hrms.domain.leave.dto.*;
import com.hrms.domain.leave.service.LeaveService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/leave")
@RequiredArgsConstructor
@Tag(name = "Leave", description = "Leave type & request management")
public class LeaveController {

    private final LeaveService leaveService;
    private final com.hrms.domain.employee.service.EmployeeService employeeService;
    private final com.hrms.domain.account.repository.UserRepository userRepository;

    // â”€â”€ Leave Types â”€â”€

    @GetMapping("/types")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or hasRole('EMPLOYEE')")
    @Operation(summary = "List all leave types")
    public ResponseEntity<ApiResponse<List<LeaveTypeResponse>>> getTypes() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(leaveService.getActiveTypes(companyId)));
    }

    @GetMapping("/types/all")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List all leave types including inactive")
    public ResponseEntity<ApiResponse<List<LeaveTypeResponse>>> getAllTypes() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(leaveService.getTypes(companyId)));
    }

    @PostMapping("/types")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Create leave type")
    public ResponseEntity<ApiResponse<LeaveTypeResponse>> createType(
            @Valid @RequestBody LeaveTypeRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(leaveService.createType(companyId, request), "Leave type created"));
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Update leave type")
    public ResponseEntity<ApiResponse<LeaveTypeResponse>> updateType(
            @PathVariable Long id, @Valid @RequestBody LeaveTypeRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(leaveService.updateType(id, companyId, request)));
    }

    // â”€â”€ Leave Requests â”€â”€

    @GetMapping("/requests/my/blocks")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Employee: get approved leave blocks for a month (used by timesheet)")
    public ResponseEntity<ApiResponse<List<LeaveBlockResponse>>> getMyLeaveBlocks(
            @RequestParam String month) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(leaveService.getLeaveBlocksForMonth(userId, month)));
    }

    @GetMapping("/requests/blocks")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Admin: get approved leave blocks for a specific user in a month")
    public ResponseEntity<ApiResponse<List<LeaveBlockResponse>>> getLeaveBlocksForUser(
            @RequestParam Long userId,
            @RequestParam String month) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        // Validate userId belongs to current company
        if (!userRepository.existsByIdAndCompanyIdAndDeletedAtIsNull(userId, companyId)) {
            throw new com.hrms.domain.common.exception.TenantAccessDeniedException(
                    "User does not belong to your company");
        }
        return ResponseEntity.ok(ApiResponse.ok(leaveService.getLeaveBlocksForMonth(userId, month)));
    }

    @GetMapping("/requests/my")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Employee: list own leave requests")
    public ResponseEntity<ApiResponse<Page<LeaveRequestResponse>>> getMyRequests(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(leaveService.listMyRequests(userId, status, pageable)));
    }

    @PostMapping("/requests/my")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Employee: submit own leave request")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> createMyRequest(
            @Valid @RequestBody CreateLeaveRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(leaveService.createMyRequest(userId, request), "Leave request submitted"));
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List all leave requests (admin). Filter by employeeId or status.")
    public ResponseEntity<ApiResponse<Page<LeaveRequestResponse>>> listRequests(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(leaveService.listRequests(companyId, employeeId, status, pageable)));
    }

    @PostMapping("/requests/employee/{publicId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or (hasRole('EMPLOYEE') and @tenantSecurity.isOwnerEmployeeByPublicId(#publicId))")
    @Operation(summary = "Submit a leave request for an employee")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> createRequest(
            @PathVariable String publicId,
            @Valid @RequestBody CreateLeaveRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(leaveService.createRequest(companyId, employeeId, request), "Leave request submitted"));
    }

    @PatchMapping("/requests/{id}/approve")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Approve a leave request")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> approve(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(leaveService.approve(id, companyId), "Request approved"));
    }

    @PatchMapping("/requests/{id}/reject")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Reject a leave request")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> reject(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(leaveService.reject(id, companyId, body.get("reason")), "Request rejected"));
    }

    @PatchMapping("/requests/{id}/cancel")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or hasRole('EMPLOYEE')")
    @Operation(summary = "Cancel a leave request")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> cancel(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(leaveService.cancel(id, companyId, userId), "Request cancelled"));
    }

    // â”€â”€ Leave Balances â”€â”€

    @GetMapping("/balances")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Láº¥y quota nghá»‰ phÃ©p cá»§a táº¥t cáº£ nhÃ¢n sá»± trong nÄƒm")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> getLeaveBalances(
            @RequestParam(defaultValue = "0") int year) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        int targetYear = year == 0 ? java.time.LocalDate.now().getYear() : year;
        return ResponseEntity.ok(ApiResponse.ok(leaveService.getLeaveBalances(companyId, targetYear)));
    }

    @GetMapping("/balances/employee/{publicId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or (hasRole('EMPLOYEE') and @tenantSecurity.isOwnerEmployeeByPublicId(#publicId))")
    @Operation(summary = "Láº¥y quota nghá»‰ phÃ©p cá»§a má»™t nhÃ¢n sá»±")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> getEmployeeLeaveBalances(
            @PathVariable String publicId,
            @RequestParam(defaultValue = "0") int year) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        int targetYear = year == 0 ? java.time.LocalDate.now().getYear() : year;
        return ResponseEntity.ok(ApiResponse.ok(leaveService.getEmployeeLeaveBalances(companyId, employeeId, targetYear)));
    }

    @PostMapping("/balances/grant")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Cáº¥p quota nghá»‰ phÃ©p cho nhÃ¢n sá»±")
    public ResponseEntity<ApiResponse<LeaveBalanceResponse>> grantLeaveBalance(
            @RequestBody Map<String, Object> body) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = Long.valueOf(body.get("employeeId").toString());
        Long leaveTypeId = Long.valueOf(body.get("leaveTypeId").toString());
        int year = body.get("year") != null ? Integer.parseInt(body.get("year").toString())
                : java.time.LocalDate.now().getYear();
        java.math.BigDecimal quota = new java.math.BigDecimal(body.get("totalQuota").toString());
        return ResponseEntity.ok(ApiResponse.ok(
                leaveService.grantLeaveBalance(companyId, employeeId, leaveTypeId, year, quota)));
    }
}
