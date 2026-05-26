package com.hrms.infrastructure.security;

import com.hrms.domain.common.enums.Role;
import com.hrms.domain.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component("tenantSecurity")
@RequiredArgsConstructor
@Slf4j
public class TenantSecurity {

    private final EmployeeRepository employeeRepository;

    public boolean isSameTenant(Long resourceCompanyId) {
        HrmsUserPrincipal principal = SecurityUtils.getCurrentUser();
        if (principal.role() == Role.SUPER_ADMIN) {
            return true;
        }

        if (resourceCompanyId != null && !resourceCompanyId.equals(principal.companyId())) {
            log.warn("CROSS-COMPANY ACCESS ATTEMPT: user={} (company={}) tried to access company={}",
                    principal.username(), principal.companyId(), resourceCompanyId);
            return false;
        }

        return resourceCompanyId != null && resourceCompanyId.equals(principal.companyId());
    }

    public boolean isOwner(Long resourceUserId) {
        return SecurityUtils.getCurrentUserId().equals(resourceUserId);
    }

    /**
     * Kiểm tra employeeId thuộc về user hiện tại.
     * Dùng khi EMPLOYEE truy cập resource theo employeeId (không phải userId).
     */
    public boolean isOwnerEmployee(Long employeeId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return employeeRepository.findByIdAndDeletedAtIsNull(employeeId)
                .map(emp -> currentUserId.equals(emp.getUserId()))
                .orElse(false);
    }

    /**
     * Kiểm tra publicId (UUID) thuộc về user hiện tại.
     * Dùng khi EMPLOYEE truy cập resource theo publicId trên API URL.
     */
    public boolean isOwnerEmployeeByPublicId(String publicId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return employeeRepository.findByPublicIdAndDeletedAtIsNull(publicId)
                .map(emp -> currentUserId.equals(emp.getUserId()))
                .orElse(false);
    }
}
