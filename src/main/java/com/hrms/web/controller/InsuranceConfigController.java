package com.hrms.web.controller;

import com.hrms.domain.insurance.dto.InsuranceConfigRequest;
import com.hrms.domain.insurance.dto.InsuranceConfigResponse;
import com.hrms.domain.insurance.service.InsuranceService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/insurance/config")
@RequiredArgsConstructor
public class InsuranceConfigController {

    private final InsuranceService insuranceService;

    /** MANAGER được xem cấu hình để biết tỷ lệ đang áp dụng */
    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<InsuranceConfigResponse>>> getAll() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.getConfigs(companyId)));
    }

    /** MANAGER được xem cấu hình active */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<InsuranceConfigResponse>>> getActive() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.getActiveConfigs(companyId)));
    }

    /** Chỉ COMPANY_ADMIN được tạo/sửa/seed cấu hình tỷ lệ */
    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<InsuranceConfigResponse>> create(
            @Valid @RequestBody InsuranceConfigRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.createConfig(companyId, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<InsuranceConfigResponse>> update(
            @PathVariable Long id, @Valid @RequestBody InsuranceConfigRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.updateConfig(id, companyId, request)));
    }

    @PostMapping("/seed")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<String>> seedDefault() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        insuranceService.seedDefaultConfig(companyId);
        return ResponseEntity.ok(ApiResponse.ok("Default insurance config seeded successfully"));
    }
}
