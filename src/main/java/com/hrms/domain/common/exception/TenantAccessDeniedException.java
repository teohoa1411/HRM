package com.hrms.domain.common.exception;

import org.springframework.http.HttpStatus;

public class TenantAccessDeniedException extends BusinessException {

    public TenantAccessDeniedException() {
        super("Access denied: resource does not belong to your company", HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED");
    }

    public TenantAccessDeniedException(String message) {
        super(message, HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED");
    }
}
