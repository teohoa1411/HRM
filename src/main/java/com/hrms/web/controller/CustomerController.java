package com.hrms.web.controller;

import com.hrms.domain.customer.dto.*;
import com.hrms.domain.customer.service.CustomerService;
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
@RequestMapping("/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer management & price configuration")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List customers for current company")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(customerService.listByCompany(companyId, pageable)));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List active customers (for dropdowns)")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> listActive() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(customerService.listActive(companyId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get customer by ID")
    public ResponseEntity<ApiResponse<CustomerResponse>> get(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(customerService.getById(id, companyId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Create customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> create(
            @Valid @RequestBody CreateCustomerRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(customerService.create(request, companyId), "Customer created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Update customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CreateCustomerRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(customerService.update(id, request, companyId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Delete customer (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        customerService.delete(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Customer deleted"));
    }

    // â”€â”€ Price endpoints â”€â”€

    @PostMapping("/{id}/prices")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Set customer price for a job role")
    public ResponseEntity<ApiResponse<Void>> setPrice(
            @PathVariable Long id, @Valid @RequestBody SetCustomerPriceRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        customerService.setPrice(id, companyId, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Customer price saved"));
    }

    @GetMapping("/{id}/prices")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get price history for a customer")
    public ResponseEntity<ApiResponse<List<CustomerPriceResponse>>> getPriceHistory(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(customerService.getPriceHistory(id, companyId)));
    }
}
