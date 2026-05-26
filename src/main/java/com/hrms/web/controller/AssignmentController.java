package com.hrms.web.controller;

import com.hrms.domain.assignment.dto.AssignmentRequest;
import com.hrms.domain.assignment.dto.AssignmentResponse;
import com.hrms.domain.assignment.service.AssignmentService;
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
@RequestMapping("/v1/assignments")
@RequiredArgsConstructor
@Tag(name = "Assignments", description = "Employee-Customer project assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final com.hrms.domain.employee.service.EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "List all assignments for company")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> listAll() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(assignmentService.getByCompany(companyId)));
    }

    @GetMapping("/employee/{publicId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Get assignments for an employee")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> getByEmployee(@PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(assignmentService.getByEmployee(employeeId, companyId)));
    }

    @PostMapping("/employee/{publicId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Assign employee to a customer project")
    public ResponseEntity<ApiResponse<AssignmentResponse>> create(
            @PathVariable String publicId, @Valid @RequestBody AssignmentRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(assignmentService.create(employeeId, companyId, request), "Assignment created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Update assignment")
    public ResponseEntity<ApiResponse<AssignmentResponse>> update(
            @PathVariable Long id, @Valid @RequestBody AssignmentRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(assignmentService.update(id, companyId, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Delete assignment")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        assignmentService.delete(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Assignment deleted"));
    }
}
