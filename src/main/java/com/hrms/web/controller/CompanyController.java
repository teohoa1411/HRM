package com.hrms.web.controller;

import com.hrms.domain.company.dto.CompanyResponse;
import com.hrms.domain.company.dto.CreateCompanyRequest;
import com.hrms.domain.company.service.CompanyService;
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
@RequestMapping("/v1/companies")
@RequiredArgsConstructor
@Tag(name = "Companies", description = "Company / Tenant management (SUPER_ADMIN only)")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List all companies")
    public ResponseEntity<ApiResponse<Page<CompanyResponse>>> listCompanies(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(companyService.listAll(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or (hasAnyRole('COMPANY_ADMIN','MANAGER') and @tenantSecurity.isSameTenant(#id))")
    @Operation(summary = "Get company by ID")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompany(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(companyService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create new company with COMPANY_ADMIN account")
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(
            @Valid @RequestBody CreateCompanyRequest request) {
        CompanyResponse response = companyService.createCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Company created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update company information")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompany(
            @PathVariable Long id,
            @Valid @RequestBody CreateCompanyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(companyService.updateCompany(id, request)));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Enable/disable company and all its users")
    public ResponseEntity<ApiResponse<Void>> toggleStatus(@PathVariable Long id) {
        companyService.toggleCompanyStatus(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Company status updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Soft delete company")
    public ResponseEntity<ApiResponse<Void>> deleteCompany(@PathVariable Long id) {
        companyService.deleteCompany(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Company deleted"));
    }
}
