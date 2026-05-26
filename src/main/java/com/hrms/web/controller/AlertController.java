package com.hrms.web.controller;

import com.hrms.domain.alert.dto.AlertCountDto;
import com.hrms.domain.alert.dto.AlertDto;
import com.hrms.domain.alert.scheduler.DailyAlertCollectorJob;
import com.hrms.domain.alert.service.AlertService;
import com.hrms.domain.common.enums.AlertCategory;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/alerts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER','ACCOUNTANT')")
public class AlertController {

    private final AlertService alertService;
    private final DailyAlertCollectorJob collectorJob;

    /** Dashboard: láº¥y táº¥t cáº£ alerts ACTIVE */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<AlertDto>>> getActive() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(alertService.getActiveAlerts(companyId)));
    }

    /** Badge sidebar: Ä‘áº¿m alerts theo severity */
    @GetMapping("/active/count")
    public ResponseEntity<ApiResponse<AlertCountDto>> getCount() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(alertService.getCounts(companyId)));
    }

    /** Filter theo category */
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<AlertDto>>> getByCategory(@PathVariable AlertCategory category) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(alertService.getByCategory(companyId, category)));
    }

    /** User dismiss alert (Ä‘Ã£ xem) */
    @PatchMapping("/{id}/dismiss")
    public ResponseEntity<ApiResponse<Void>> dismiss(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        alertService.dismiss(id, companyId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Alert dismissed"));
    }

    /** Resolve alert (Ä‘Ã£ xá»­ lÃ½) */
    @PatchMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<Void>> resolve(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        alertService.resolve(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Alert resolved"));
    }

    /** Admin: trigger cháº¡y job thá»§ cÃ´ng */
    @PostMapping("/collect-now")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> collectNow() {
        collectorJob.runManual();
        return ResponseEntity.ok(ApiResponse.ok("Alert collection completed"));
    }
}
