package com.hrms.web.controller;

import com.hrms.domain.common.enums.DeclarationStatus;
import com.hrms.domain.insurance.dto.*;
import com.hrms.domain.insurance.service.InsuranceDeclarationService;
import com.hrms.domain.insurance.service.InsuranceService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/insurance")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
public class InsuranceController {

    private final InsuranceService insuranceService;
    private final InsuranceDeclarationService declarationService;

    // ==================== POLICY ====================

    @GetMapping("/policies")
    public ResponseEntity<ApiResponse<List<InsurancePolicyResponse>>> getPolicies() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.getPolicies(companyId)));
    }

    @GetMapping("/policies/active")
    public ResponseEntity<ApiResponse<List<InsurancePolicyResponse>>> getActivePolicies() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.getActivePolicies(companyId)));
    }

    @GetMapping("/policies/employee/{employeeId}")
    public ResponseEntity<ApiResponse<List<InsurancePolicyResponse>>> getByEmployee(@PathVariable Long employeeId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.getByEmployee(employeeId, companyId)));
    }

    @PostMapping("/policies")
    public ResponseEntity<ApiResponse<InsurancePolicyResponse>> createPolicy(
            @Valid @RequestBody InsurancePolicyRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.createPolicy(companyId, request)));
    }

    @PutMapping("/policies/{id}")
    public ResponseEntity<ApiResponse<InsurancePolicyResponse>> updatePolicy(
            @PathVariable Long id, @Valid @RequestBody InsurancePolicyRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.updatePolicy(id, companyId, request)));
    }

    /** TÃ­nh tiá»n BH hÃ ng thÃ¡ng cho má»™t NV */
    @GetMapping("/calculate/{employeeId}")
    public ResponseEntity<ApiResponse<InsuranceCalculationResult>> calculate(@PathVariable Long employeeId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.calculateMonthly(employeeId, companyId, LocalDate.now().withDayOfMonth(1))));
    }

    // ==================== DECLARATIONS ====================

    @GetMapping("/declarations")
    public ResponseEntity<ApiResponse<List<InsuranceDeclarationResponse>>> getDeclarations() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(declarationService.getAll(companyId)));
    }

    @GetMapping("/declarations/month/{month}")
    public ResponseEntity<ApiResponse<List<InsuranceDeclarationResponse>>> getByMonth(@PathVariable String month) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(declarationService.getByMonth(companyId, month)));
    }

    @GetMapping("/declarations/pending")
    public ResponseEntity<ApiResponse<List<InsuranceDeclarationResponse>>> getPending() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(declarationService.getPending(companyId)));
    }

    @GetMapping("/declarations/upcoming")
    public ResponseEntity<ApiResponse<List<InsuranceDeclarationResponse>>> getUpcoming(
            @RequestParam(defaultValue = "15") int days) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(declarationService.getUpcoming(companyId, days)));
    }

    @GetMapping("/declarations/overdue")
    public ResponseEntity<ApiResponse<List<InsuranceDeclarationResponse>>> getOverdue() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(declarationService.getOverdue(companyId)));
    }

    @PostMapping("/declarations")
    public ResponseEntity<ApiResponse<InsuranceDeclarationResponse>> createDeclaration(
            @Valid @RequestBody InsuranceDeclarationRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(declarationService.create(companyId, request)));
    }

    @PatchMapping("/declarations/{id}/status")
    public ResponseEntity<ApiResponse<InsuranceDeclarationResponse>> updateDeclarationStatus(
            @PathVariable Long id, @RequestParam DeclarationStatus status) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(declarationService.updateStatus(id, companyId, status)));
    }
}
