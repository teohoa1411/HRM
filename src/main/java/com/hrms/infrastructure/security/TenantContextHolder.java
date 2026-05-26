package com.hrms.infrastructure.security;

public class TenantContextHolder {

    private static final ThreadLocal<Long> currentTenant = new InheritableThreadLocal<>();

    private TenantContextHolder() {}

    public static void setCurrentTenant(Long companyId) {
        currentTenant.set(companyId);
    }

    public static Long getCurrentTenant() {
        return currentTenant.get();
    }

    public static void clear() {
        currentTenant.remove();
    }
}
