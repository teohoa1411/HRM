package com.hrms.web.controller;

import com.hrms.domain.account.dto.CreateUserRequest;
import com.hrms.domain.account.dto.UserResponse;
import com.hrms.domain.account.service.UserService;
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

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User account management")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "List users for current company")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(userService.listByCompany(companyId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(userService.getById(id, companyId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(userService.createUser(request, companyId), "User created"));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Enable/disable user account")
    public ResponseEntity<ApiResponse<Void>> toggleStatus(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        userService.toggleUserStatus(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Status updated"));
    }

    @PatchMapping("/{id}/unlock")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Unlock locked account")
    public ResponseEntity<ApiResponse<Void>> unlock(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        userService.unlockUser(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Account unlocked"));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Change user role")
    public ResponseEntity<ApiResponse<Void>> changeRole(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        String newRole = body.get("role");
        userService.changeRole(id, companyId, newRole);
        return ResponseEntity.ok(ApiResponse.ok(null, "Role updated"));
    }

    @PatchMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Reset user password to default")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        userService.resetPasswordToDefault(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Password reset to default"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','MANAGER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        userService.deleteUser(id, companyId);
        return ResponseEntity.ok(ApiResponse.ok(null, "User deleted"));
    }
}
