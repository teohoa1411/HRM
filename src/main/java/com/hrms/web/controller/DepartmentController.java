package com.hrms.web.controller;

import com.hrms.domain.company.dto.DepartmentRequest;
import com.hrms.domain.company.dto.DepartmentResponse;
import com.hrms.domain.company.service.DepartmentService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/departments")
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Department structure management")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List all departments for current company")
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(departmentService.list(SecurityUtils.getCurrentCompanyId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(departmentService.getById(id, SecurityUtils.getCurrentCompanyId())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(departmentService.create(request, SecurityUtils.getCurrentCompanyId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> update(
            @PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(departmentService.update(id, request, SecurityUtils.getCurrentCompanyId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        departmentService.delete(id, SecurityUtils.getCurrentCompanyId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Department deleted"));
    }
}
