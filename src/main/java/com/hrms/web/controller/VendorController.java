package com.hrms.web.controller;

import com.hrms.domain.vendor.dto.CreateVendorRequest;
import com.hrms.domain.vendor.dto.SetVendorPriceRequest;
import com.hrms.domain.vendor.dto.VendorPriceResponse;
import com.hrms.domain.vendor.dto.VendorResponse;
import com.hrms.domain.vendor.service.VendorPriceService;
import com.hrms.domain.vendor.service.VendorService;
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
@RequestMapping("/v1/vendors")
@RequiredArgsConstructor
@Tag(name = "Vendors", description = "Vendor / supplier management")
public class VendorController {

    private final VendorService vendorService;
    private final VendorPriceService vendorPriceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List vendors for current company")
    public ResponseEntity<ApiResponse<Page<VendorResponse>>> listVendors(
            @PageableDefault(size = 20) Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(vendorService.listByCompany(companyId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or hasRole('VENDOR_USER')")
    @Operation(summary = "Get vendor by ID")
    public ResponseEntity<ApiResponse<VendorResponse>> getVendor(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(vendorService.getById(id, companyId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Create vendor with auto-generated VENDOR_USER account")
    public ResponseEntity<ApiResponse<VendorResponse>> createVendor(
            @Valid @RequestBody CreateVendorRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        VendorResponse response = vendorService.createVendor(request, companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Vendor created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Update vendor information")
    public ResponseEntity<ApiResponse<VendorResponse>> updateVendor(
            @PathVariable Long id,
            @Valid @RequestBody CreateVendorRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(vendorService.updateVendor(id, request, companyId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Delete vendor (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteVendor(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        vendorService.deleteVendor(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Vendor deleted"));
    }

    // â”€â”€ Price endpoints â”€â”€

    @PostMapping("/{id}/prices")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Set vendor price for a job role")
    public ResponseEntity<ApiResponse<Void>> setPrice(
            @PathVariable Long id, @Valid @RequestBody SetVendorPriceRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        vendorPriceService.setPrice(id, companyId, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Vendor price saved"));
    }

    @GetMapping("/{id}/prices")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get price history for a vendor")
    public ResponseEntity<ApiResponse<java.util.List<VendorPriceResponse>>> getPriceHistory(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(vendorPriceService.getPriceHistory(id, companyId)));
    }
}
