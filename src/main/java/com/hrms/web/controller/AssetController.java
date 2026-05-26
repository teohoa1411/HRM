package com.hrms.web.controller;

import com.hrms.domain.asset.dto.AssetAssignmentResponse;
import com.hrms.domain.asset.dto.AssetResponse;
import com.hrms.domain.asset.dto.AssignAssetRequest;
import com.hrms.domain.asset.dto.CreateAssetRequest;
import com.hrms.domain.asset.dto.ReturnAssetRequest;
import com.hrms.domain.asset.service.AssetService;
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

@RestController
@RequestMapping("/v1/assets")
@RequiredArgsConstructor
@Tag(name = "Assets", description = "Asset management")
public class AssetController {

    private final AssetService assetService;
    private final com.hrms.domain.employee.service.EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List assets for current company with optional filters")
    public ResponseEntity<ApiResponse<Page<AssetResponse>>> listAssets(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String ownershipType,
            @RequestParam(required = false) String usedBy) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(
                assetService.listByCompany(companyId, pageable, status, categoryId, ownershipType, usedBy)
        ));
    }

    @GetMapping("/next-code")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get next auto-generated asset code")
    public ResponseEntity<ApiResponse<String>> getNextCode() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(assetService.getNextCode(companyId)));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get asset statistics for current company")
    public ResponseEntity<ApiResponse<AssetService.AssetStatsDto>> getStats() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(assetService.getStats(companyId)));
    }

    @GetMapping("/warranty-alerts")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get assets with warranty expiring within 30 days")
    public ResponseEntity<ApiResponse<List<AssetResponse>>> getWarrantyAlerts() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(assetService.getWarrantyAlerts(companyId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get asset by ID")
    public ResponseEntity<ApiResponse<AssetResponse>> getAsset(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(assetService.getById(id, companyId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Create asset with auto-generated code")
    public ResponseEntity<ApiResponse<AssetResponse>> createAsset(
            @Valid @RequestBody CreateAssetRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        AssetResponse response = assetService.create(request, companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Asset created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Update asset information")
    public ResponseEntity<ApiResponse<AssetResponse>> updateAsset(
            @PathVariable Long id,
            @Valid @RequestBody CreateAssetRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(assetService.update(id, request, companyId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Delete asset (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        assetService.delete(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Asset deleted"));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Assign asset to employee")
    public ResponseEntity<ApiResponse<AssetResponse>> assignAsset(
            @PathVariable Long id,
            @Valid @RequestBody AssignAssetRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();
        AssetResponse response = assetService.assignAsset(id, request, companyId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Asset assigned successfully"));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Return asset from employee")
    public ResponseEntity<ApiResponse<AssetResponse>> returnAsset(
            @PathVariable Long id,
            @Valid @RequestBody ReturnAssetRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        AssetResponse response = assetService.returnAsset(id, request, companyId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Asset returned successfully"));
    }

    @GetMapping("/{id}/assignments")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get assignment history for an asset")
    public ResponseEntity<ApiResponse<List<AssetAssignmentResponse>>> getAssignmentHistory(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(assetService.getAssignmentHistory(id, companyId)));
    }

    @GetMapping("/employee/{publicId}/assets")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or (hasRole('EMPLOYEE') and @tenantSecurity.isOwnerEmployeeByPublicId(#publicId))")
    @Operation(summary = "Get active assets held by an employee")
    public ResponseEntity<ApiResponse<List<AssetResponse>>> getEmployeeAssets(@PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(assetService.getEmployeeAssets(employeeId, companyId)));
    }

    @GetMapping("/employee/{publicId}/assignments")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or (hasRole('EMPLOYEE') and @tenantSecurity.isOwnerEmployeeByPublicId(#publicId))")
    @Operation(summary = "Get all asset assignment history for an employee (active + returned)")
    public ResponseEntity<ApiResponse<List<AssetAssignmentResponse>>> getEmployeeAssignmentHistory(@PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(assetService.getEmployeeAssignmentHistory(employeeId, companyId)));
    }
}
