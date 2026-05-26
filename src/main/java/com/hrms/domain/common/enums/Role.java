package com.hrms.domain.common.enums;

public enum Role {
    SUPER_ADMIN,
    COMPANY_ADMIN,
    MANAGER,       // HR + Kế toán: toàn quyền quản lý nội bộ
    ACCOUNTANT,
    VENDOR_USER,
    EMPLOYEE
}
