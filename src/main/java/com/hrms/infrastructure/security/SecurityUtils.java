package com.hrms.infrastructure.security;

import com.hrms.domain.common.enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    private SecurityUtils() {}

    public static HrmsUserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return (HrmsUserPrincipal) authentication.getPrincipal();
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().userId();
    }

    public static Long getCurrentCompanyId() {
        return getCurrentUser().companyId();
    }

    public static Role getCurrentRole() {
        return getCurrentUser().role();
    }

    public static boolean isSuperAdmin() {
        return getCurrentRole() == Role.SUPER_ADMIN;
    }

    /** Returns true for either COMPANY_ADMIN or MANAGER. */
    public static boolean isAdminOrManager() {
        Role role = getCurrentRole();
        return role == Role.COMPANY_ADMIN || role == Role.MANAGER;
    }

    public static boolean isAccountant() {
        Role role = getCurrentRole();
        return role == Role.ACCOUNTANT || role == Role.MANAGER;
    }
}
