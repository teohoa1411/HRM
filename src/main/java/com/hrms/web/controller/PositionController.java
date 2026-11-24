package com.hrms.web.controller;

import com.hrms.domain.company.dto.PositionRequest;
import com.hrms.domain.company.dto.PositionResponse;
import com.hrms.domain.company.service.PositionService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/positions")
@RequiredArgsConstructor
@Tag(name = "Positions", description = "Internal position management per company")
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<PositionResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(positionService.list(SecurityUtils.getCurrentCompanyId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<PositionResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(positionService.getById(id, SecurityUtils.getCurrentCompanyId())));
    }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<PositionResponse>> create(@Valid @RequestBody PositionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(positionService.create(request, SecurityUtils.getCurrentCompanyId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<PositionResponse>> update(
            @PathVariable Long id, @Valid @RequestBody PositionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(positionService.update(id, request, SecurityUtils.getCurrentCompanyId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        positionService.delete(id, SecurityUtils.getCurrentCompanyId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Position deleted"));
    }
}
