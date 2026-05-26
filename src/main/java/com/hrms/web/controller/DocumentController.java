package com.hrms.web.controller;

import com.hrms.domain.common.enums.DocumentType;
import com.hrms.domain.document.dto.DocumentResponse;
import com.hrms.domain.document.service.DocumentService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.util.List;

@RestController
@RequestMapping("/v1/employees/{publicId}/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Employee document management (S3)")
public class DocumentController {

    private final DocumentService documentService;
    private final com.hrms.domain.employee.service.EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or (hasRole('EMPLOYEE') and @tenantSecurity.isOwnerEmployeeByPublicId(#publicId))")
    @Operation(summary = "List documents for an employee")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> listDocuments(@PathVariable String publicId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(documentService.listByEmployee(employeeId, companyId)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Upload document for employee to S3")
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @PathVariable String publicId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") DocumentType documentType) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Long employeeId = employeeService.resolveEmployeeId(publicId, companyId);
        DocumentResponse response = documentService.uploadDocument(employeeId, companyId, file, documentType);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Document uploaded"));
    }

    @GetMapping("/{docId}/url")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or (hasRole('EMPLOYEE') and @tenantSecurity.isOwnerEmployeeByPublicId(#publicId))")
    @Operation(summary = "Get pre-signed URL (valid 15 min) to download document")
    public ResponseEntity<ApiResponse<String>> getPresignedUrl(
            @PathVariable String publicId,
            @PathVariable Long docId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        URL url = documentService.getPresignedUrl(docId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(url.toString()));
    }

    @DeleteMapping("/{docId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER')")
    @Operation(summary = "Delete document (soft delete + remove from S3)")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable String publicId,
            @PathVariable Long docId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        documentService.deleteDocument(docId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Document deleted"));
    }
}
