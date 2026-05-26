package com.hrms.web.controller;

import com.hrms.domain.asset.dto.AssetCategoryResponse;
import com.hrms.domain.asset.dto.CreateAssetCategoryRequest;
import com.hrms.domain.asset.service.AssetCategoryService;
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
@RequestMapping("/v1/asset-categories")
@RequiredArgsConstructor
@Tag(name = "Asset Categories", description = "Asset category management")
public class AssetCategoryController {

    private final AssetCategoryService assetCategoryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List asset categories for current company")
    public ResponseEntity<ApiResponse<Page<AssetCategoryResponse>>> listCategories(
            @PageableDefault(size = 20) Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(assetCategoryService.listByCompany(companyId, pageable)));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List active asset categories (for dropdown)")
    public ResponseEntity<ApiResponse<List<AssetCategoryResponse>>> listActiveCategories() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(assetCategoryService.listActive(companyId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get asset category by ID")
    public ResponseEntity<ApiResponse<AssetCategoryResponse>> getCategory(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(assetCategoryService.getById(id, companyId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Create asset category")
    public ResponseEntity<ApiResponse<AssetCategoryResponse>> createCategory(
            @Valid @RequestBody CreateAssetCategoryRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        AssetCategoryResponse response = assetCategoryService.create(request, companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Asset category created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Update asset category")
    public ResponseEntity<ApiResponse<AssetCategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CreateAssetCategoryRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(assetCategoryService.update(id, request, companyId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Delete asset category (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        assetCategoryService.delete(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Asset category deleted"));
    }
}
