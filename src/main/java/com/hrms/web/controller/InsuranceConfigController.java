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
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
public class InsuranceConfigController {

    private final InsuranceService insuranceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<InsuranceConfigResponse>>> getAll() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.getConfigs(companyId)));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<InsuranceConfigResponse>>> getActive() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.getActiveConfigs(companyId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InsuranceConfigResponse>> create(
            @Valid @RequestBody InsuranceConfigRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.createConfig(companyId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InsuranceConfigResponse>> update(
            @PathVariable Long id, @Valid @RequestBody InsuranceConfigRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(insuranceService.updateConfig(id, companyId, request)));
    }

    /** Seed config máº·c Ä‘á»‹nh cho company */
    @PostMapping("/seed")
    public ResponseEntity<ApiResponse<String>> seedDefault() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        insuranceService.seedDefaultConfig(companyId);
        return ResponseEntity.ok(ApiResponse.ok("Default insurance config seeded successfully"));
    }
}
