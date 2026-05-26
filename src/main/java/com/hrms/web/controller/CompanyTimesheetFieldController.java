package com.hrms.web.controller;

import com.hrms.domain.timesheet.dto.CompanyTimesheetFieldRequest;
import com.hrms.domain.timesheet.dto.CompanyTimesheetFieldResponse;
import com.hrms.domain.timesheet.service.CompanyTimesheetFieldService;
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
@RequestMapping("/v1/timesheet-fields")
@RequiredArgsConstructor
@Tag(name = "Timesheet Custom Fields")
public class CompanyTimesheetFieldController {

    private final CompanyTimesheetFieldService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','EMPLOYEE')")
    @Operation(summary = "List active custom fields for my company")
    public ResponseEntity<ApiResponse<List<CompanyTimesheetFieldResponse>>> listActive() {
        return ResponseEntity.ok(ApiResponse.ok(service.listActive(SecurityUtils.getCurrentCompanyId())));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List all custom fields (including inactive) â€” admin")
    public ResponseEntity<ApiResponse<List<CompanyTimesheetFieldResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.listAll(SecurityUtils.getCurrentCompanyId())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<CompanyTimesheetFieldResponse>> create(
            @Valid @RequestBody CompanyTimesheetFieldRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.create(SecurityUtils.getCurrentCompanyId(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<CompanyTimesheetFieldResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CompanyTimesheetFieldRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, SecurityUtils.getCurrentCompanyId(), req)));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Toggle active/inactive")
    public ResponseEntity<ApiResponse<Void>> toggle(@PathVariable Long id) {
        service.toggleActive(id, SecurityUtils.getCurrentCompanyId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Toggled"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id, SecurityUtils.getCurrentCompanyId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }
}
