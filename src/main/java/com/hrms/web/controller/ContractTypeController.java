package com.hrms.web.controller;

import com.hrms.domain.common.enums.ContractCategory;
import com.hrms.domain.contract.dto.ContractTypeRequest;
import com.hrms.domain.contract.dto.ContractTypeResponse;
import com.hrms.domain.contract.service.ContractTypeService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/contract-types")
@RequiredArgsConstructor
@Tag(name = "Contract Types", description = "Quáº£n lÃ½ danh má»¥c loáº¡i há»£p Ä‘á»“ng / biÃªn báº£n")
public class ContractTypeController {

    private final ContractTypeService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or hasRole('EMPLOYEE')")
    @Operation(summary = "List active contract types")
    public ResponseEntity<ApiResponse<List<ContractTypeResponse>>> getActiveTypes(
            @RequestParam(required = false) ContractCategory category) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        List<ContractTypeResponse> result = category != null
                ? service.getByCategory(companyId, category)
                : service.getActiveTypes(companyId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List all contract types including inactive")
    public ResponseEntity<ApiResponse<List<ContractTypeResponse>>> getAllTypes() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(service.getAllTypes(companyId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Create contract type")
    public ResponseEntity<ApiResponse<ContractTypeResponse>> create(
            @Valid @RequestBody ContractTypeRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(companyId, request), "Contract type created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Update contract type")
    public ResponseEntity<ApiResponse<ContractTypeResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ContractTypeRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, companyId, request)));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Toggle active/inactive")
    public ResponseEntity<ApiResponse<Void>> toggle(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        service.toggleActive(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Status updated"));
    }
}
