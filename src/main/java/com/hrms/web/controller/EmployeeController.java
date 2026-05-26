package com.hrms.web.controller;

import com.hrms.domain.common.enums.EmployeeStatus;
import com.hrms.domain.employee.dto.CreateEmployeeRequest;
import com.hrms.domain.employee.dto.EmployeeResponse;
import com.hrms.domain.account.service.UserService;
import com.hrms.domain.employee.service.EmployeeService;
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

@RestController
@RequestMapping("/v1/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Employee management")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final UserService userService;

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Search employees by name or code")
    public ResponseEntity<ApiResponse<java.util.List<EmployeeResponse>>> searchEmployees(
            @RequestParam("q") String term) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(employeeService.searchEmployees(companyId, term)));
    }

    @GetMapping("/next-code")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Preview next auto-generated employee code")
    public ResponseEntity<ApiResponse<String>> nextCode() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(employeeService.getNextCode(companyId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List employees for current company")
    public ResponseEntity<ApiResponse<Page<EmployeeResponse>>> listEmployees(
            @PageableDefault(size = 20, sort = "fullName") Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(employeeService.listByCompany(companyId, pageable)));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or (hasRole('EMPLOYEE') and @tenantSecurity.isOwnerEmployeeByPublicId(#publicId))")
    @Operation(summary = "Get employee by public ID (UUID)")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getEmployee(@PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(employeeService.getByPublicId(publicId, companyId)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Get own employee profile")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getMyProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(employeeService.getByUserId(userId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Create employee with auto-generated EMPLOYEE account")
    public ResponseEntity<ApiResponse<EmployeeResponse>> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        EmployeeResponse response = employeeService.createEmployee(request, companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Employee created successfully"));
    }

    @PatchMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Update employee information")
    public ResponseEntity<ApiResponse<EmployeeResponse>> updateEmployee(
            @PathVariable String publicId,
            @Valid @RequestBody CreateEmployeeRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long id = employeeService.resolveEmployeeId(publicId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(employeeService.updateEmployee(id, request, companyId)));
    }

    @PatchMapping("/{publicId}/status")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Update employee status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable String publicId,
            @RequestParam EmployeeStatus status) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long id = employeeService.resolveEmployeeId(publicId, companyId);
        employeeService.updateStatus(id, companyId, status);
        return ResponseEntity.ok(ApiResponse.ok(null, "Status updated"));
    }

    @PostMapping("/{publicId}/reset-password")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Reset employee account password to Admin@123")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        var emp = employeeService.getByPublicId(publicId, companyId);
        if (emp.userId() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("NhÃ¢n viÃªn nÃ y chÆ°a cÃ³ tÃ i khoáº£n há»‡ thá»‘ng", "NO_ACCOUNT"));
        }
        userService.resetPasswordToDefault(emp.userId(), companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "ÄÃ£ reset máº­t kháº©u vá» Admin@123"));
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Delete employee (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteEmployee(@PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long id = employeeService.resolveEmployeeId(publicId, companyId);
        employeeService.deleteEmployee(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Employee deleted"));
    }
}
