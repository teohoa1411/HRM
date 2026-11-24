package com.hrms.web.controller;

import com.hrms.domain.jobrole.dto.CreateJobRoleRequest;
import com.hrms.domain.jobrole.dto.JobRoleResponse;
import com.hrms.domain.jobrole.dto.SetJobRolePriceRequest;
import com.hrms.domain.jobrole.entity.JobRolePrice;
import com.hrms.domain.jobrole.service.JobRoleService;
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
@RequestMapping("/v1/job-roles")
@RequiredArgsConstructor
@Tag(name = "Job Roles", description = "Quản lý job role theo từng công ty")
public class JobRoleController {

    private final JobRoleService jobRoleService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Danh sách job roles của công ty hiện tại")
    public ResponseEntity<ApiResponse<Page<JobRoleResponse>>> listJobRoles(
            @PageableDefault(size = 50) Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(jobRoleService.listAll(companyId, pageable)));
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách job roles đang active (dùng cho dropdown)")
    public ResponseEntity<ApiResponse<List<JobRoleResponse>>> listActiveJobRoles() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(jobRoleService.listActive(companyId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Lấy chi tiết job role")
    public ResponseEntity<ApiResponse<JobRoleResponse>> getJobRole(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(jobRoleService.getById(id, companyId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Tạo job role mới cho công ty")
    public ResponseEntity<ApiResponse<JobRoleResponse>> createJobRole(
            @Valid @RequestBody CreateJobRoleRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(jobRoleService.createJobRole(companyId, request), "Đã tạo job role"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Cập nhật job role")
    public ResponseEntity<ApiResponse<JobRoleResponse>> updateJobRole(
            @PathVariable Long id,
            @Valid @RequestBody CreateJobRoleRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(jobRoleService.updateJobRole(id, companyId, request)));
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Bật/tắt trạng thái active của job role")
    public ResponseEntity<ApiResponse<Void>> toggleActive(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        jobRoleService.toggleActive(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã cập nhật trạng thái"));
    }

    @PostMapping("/{id}/prices")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Đặt giá cho job role trong công ty hiện tại")
    public ResponseEntity<ApiResponse<Void>> setPrice(
            @PathVariable Long id,
            @Valid @RequestBody SetJobRolePriceRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        jobRoleService.setPrice(id, companyId, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã cập nhật giá"));
    }

    @GetMapping("/{id}/prices")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Lịch sử giá của job role trong công ty hiện tại")
    public ResponseEntity<ApiResponse<List<JobRolePrice>>> getPriceHistory(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(jobRoleService.getPriceHistory(id, companyId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Xóa job role (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteJobRole(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        jobRoleService.deleteJobRole(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã xóa job role"));
    }
}
