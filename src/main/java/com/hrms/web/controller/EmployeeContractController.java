package com.hrms.web.controller;

import com.hrms.domain.contract.dto.EmployeeContractGroupDto;
import com.hrms.domain.contract.dto.EmployeeContractRequest;
import com.hrms.domain.contract.dto.EmployeeContractResponse;
import com.hrms.domain.contract.dto.ExtendContractRequest;
import com.hrms.domain.contract.dto.RenewContractRequest;
import com.hrms.domain.contract.service.EmployeeContractService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "Employee Contracts", description = "Quáº£n lÃ½ há»£p Ä‘á»“ng / biÃªn báº£n nhÃ¢n sá»±")
public class EmployeeContractController {

    // â”€â”€ Group by Employee â”€â”€

    @GetMapping("/by-employee")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Danh sÃ¡ch HÄ gom theo nhÃ¢n viÃªn (má»—i dÃ²ng = 1 NV)")
    public ResponseEntity<ApiResponse<List<EmployeeContractGroupDto>>> getGroupedByEmployee() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(contractService.getGroupedByEmployee(companyId)));
    }

    private final EmployeeContractService contractService;
    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List contracts with optional filter by contractTypeId")
    public ResponseEntity<ApiResponse<Page<EmployeeContractResponse>>> list(
            @RequestParam(required = false) Long contractTypeId,
            @PageableDefault(size = 20) Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(contractService.list(companyId, contractTypeId, pageable)));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get contract detail by public ID")
    public ResponseEntity<ApiResponse<EmployeeContractResponse>> getById(@PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(contractService.getByPublicId(publicId, companyId)));
    }

    @GetMapping("/employee/{employeePublicId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or (hasRole('EMPLOYEE') and @tenantSecurity.isOwnerEmployeeByPublicId(#employeePublicId))")
    @Operation(summary = "Get all contracts/commitments for an employee")
    public ResponseEntity<ApiResponse<List<EmployeeContractResponse>>> getByEmployee(
            @PathVariable String employeePublicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(employeePublicId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(contractService.getByEmployee(employeeId, companyId)));
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List contracts expiring soon (default within 30 days)")
    public ResponseEntity<ApiResponse<List<EmployeeContractResponse>>> getExpiring(
            @RequestParam(defaultValue = "30") int withinDays) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(contractService.getExpiring(companyId, withinDays)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Create new contract/commitment record")
    public ResponseEntity<ApiResponse<EmployeeContractResponse>> create(
            @Valid @RequestBody EmployeeContractRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(contractService.create(request, companyId), "Contract created"));
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Update contract record")
    public ResponseEntity<ApiResponse<EmployeeContractResponse>> update(
            @PathVariable String publicId,
            @Valid @RequestBody EmployeeContractRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(contractService.update(publicId, request, companyId)));
    }

    @PatchMapping("/{publicId}/extend")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Gia háº¡n HÄ hiá»‡n táº¡i (kÃ©o dÃ i thÃªm thá»i háº¡n, khÃ´ng táº¡o HÄ má»›i)")
    public ResponseEntity<ApiResponse<EmployeeContractResponse>> extend(
            @PathVariable String publicId,
            @Valid @RequestBody ExtendContractRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(
                contractService.extend(publicId, request, companyId), "ÄÃ£ gia háº¡n há»£p Ä‘á»“ng"));
    }

    @PostMapping("/{publicId}/renew")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "TÃ¡i kÃ½ HÄ má»›i (HÄ cÅ© Ä‘Ã¡nh dáº¥u háº¿t háº¡n, táº¡o HÄ má»›i ná»‘i tiáº¿p)")
    public ResponseEntity<ApiResponse<EmployeeContractResponse>> renew(
            @PathVariable String publicId,
            @Valid @RequestBody RenewContractRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(contractService.renew(publicId, request, companyId), "ÄÃ£ táº¡o há»£p Ä‘á»“ng má»›i"));
    }

    @PatchMapping("/{publicId}/terminate")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Terminate a contract")
    public ResponseEntity<ApiResponse<Void>> terminate(
            @PathVariable String publicId,
            @RequestBody Map<String, String> body) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        contractService.terminate(publicId, companyId, body.get("reason"));
        return ResponseEntity.ok(ApiResponse.ok(null, "Contract terminated"));
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Delete contract (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        contractService.delete(publicId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Contract deleted"));
    }

    @PostMapping("/refresh-statuses")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Refresh contract statuses (mark expired contracts)")
    public ResponseEntity<ApiResponse<String>> refreshStatuses() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        int count = contractService.refreshStatuses(companyId);
        return ResponseEntity.ok(ApiResponse.ok("Updated " + count + " expired contracts"));
    }
}
