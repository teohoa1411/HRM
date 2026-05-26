package com.hrms.web.controller;

import com.hrms.domain.salary.dto.SalaryResponse;
import com.hrms.domain.salary.dto.SetSalaryRequest;
import com.hrms.domain.salary.service.InternalSalaryService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/employees/{publicId}/salary")
@RequiredArgsConstructor
@Tag(name = "Salary", description = "Internal employee salary management")
public class SalaryController {

    private final InternalSalaryService salaryService;
    private final com.hrms.domain.employee.service.EmployeeService employeeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Set salary for an internal employee")
    public ResponseEntity<ApiResponse<Void>> setSalary(
            @PathVariable String publicId,
            @Valid @RequestBody SetSalaryRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        salaryService.setSalary(employeeId, companyId, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Salary configured"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get salary history for an employee")
    public ResponseEntity<ApiResponse<List<SalaryResponse>>> getHistory(@PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(salaryService.getHistory(employeeId, companyId)));
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get current active salary for an employee")
    public ResponseEntity<ApiResponse<SalaryResponse>> getCurrent(@PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(salaryService.getCurrent(employeeId, companyId)));
    }
}
