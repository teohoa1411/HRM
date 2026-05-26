package com.hrms.web.controller;

import com.hrms.domain.document.sync.dto.SyncLogDto;
import com.hrms.domain.document.sync.dto.SyncSessionDto;
import com.hrms.domain.document.sync.dto.SyncTriggerResponse;
import com.hrms.domain.document.sync.service.DocumentSyncService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/documents/sync")
@RequiredArgsConstructor
@Tag(name = "Document Sync", description = "Äá»“ng bá»™ tÃ i liá»‡u nhÃ¢n sá»± tá»« S3 ra Ä‘Ã­ch (Local/S3 Backup)")
public class DocumentSyncController {

    private final DocumentSyncService syncService;

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Báº¯t Ä‘áº§u Ä‘á»“ng bá»™ (async) â€” tráº£ vá» sessionId Ä‘á»ƒ FE polling")
    public ResponseEntity<ApiResponse<SyncTriggerResponse>> triggerSync() {
        Long companyId   = SecurityUtils.getCurrentCompanyId();
        Long triggeredBy = SecurityUtils.getCurrentUserId();
        SyncTriggerResponse response = syncService.triggerSync(companyId, triggeredBy);
        return ResponseEntity.accepted().body(ApiResponse.ok(response, response.message()));
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Tráº¡ng thÃ¡i session Ä‘ang cháº¡y (náº¿u cÃ³)")
    public ResponseEntity<ApiResponse<SyncSessionDto>> getRunningStatus() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        SyncSessionDto dto = syncService.getRunningSession(companyId).orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Lá»‹ch sá»­ cÃ¡c láº§n Ä‘á»“ng bá»™ (phÃ¢n trang)")
    public ResponseEntity<ApiResponse<Page<SyncSessionDto>>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok(syncService.getHistory(companyId, pageable)));
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Chi tiáº¿t logs cá»§a 1 session (phÃ¢n trang)")
    public ResponseEntity<ApiResponse<Page<SyncLogDto>>> getSessionLogs(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok(syncService.getSessionLogs(sessionId, companyId, pageable)));
    }
}
