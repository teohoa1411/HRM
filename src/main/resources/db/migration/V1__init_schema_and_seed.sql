-- =============================================================================
-- CONSOLIDATED DATABASE SCHEMA MIGRATION V1
-- =============================================================================
-- Purpose: Complete database initialization for first Production deployment
-- This migration consolidates all table definitions and seed data from V1-V48
-- into a single, idempotent initialization script.
--
-- Structure:
-- 1. Disable foreign key checks for safe initialization
-- 2. Create all tables in proper dependency order
-- 3. Re-enable foreign key checks
-- 4. Insert all seed data with idempotency (INSERT ... SELECT ... WHERE NOT EXISTS)
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- CORE INFRASTRUCTURE TABLES
-- =============================================================================

CREATE TABLE companies (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code                VARCHAR(20)     NOT NULL UNIQUE COMMENT 'Unique company code e.g. VNTECH01',
    name                VARCHAR(255)    NOT NULL,
    short_name          VARCHAR(100)    NULL,
    tax_code            VARCHAR(20)     NULL UNIQUE COMMENT 'Tax identification number',
    business_license    VARCHAR(100)    NULL,
    address             VARCHAR(500)    NULL,
    city                VARCHAR(100)    NULL,
    country             VARCHAR(100)    NOT NULL DEFAULT 'VN',
    phone               VARCHAR(20)     NULL,
    email               VARCHAR(255)    NULL,
    website             VARCHAR(255)    NULL,
    logo_s3_key         VARCHAR(500)    NULL,
    admin_user_id       BIGINT UNSIGNED NULL,
    is_active           TINYINT(1)      NOT NULL DEFAULT 1,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by          BIGINT UNSIGNED NULL,
    updated_by          BIGINT UNSIGNED NULL,
    deleted_at          DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_company_code (code),
    UNIQUE KEY uq_company_tax_code (tax_code),
    INDEX idx_company_is_active (is_active),
    INDEX idx_company_deleted_at (deleted_at),
    CONSTRAINT fk_companies_admin_user FOREIGN KEY (admin_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Tenant table - each row is an independent company';

CREATE TABLE users (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username             VARCHAR(100)    NOT NULL UNIQUE,
    email                VARCHAR(255)    NOT NULL UNIQUE,
    password_hash        VARCHAR(255)    NOT NULL COMMENT 'BCrypt encoded, cost=12',
    role                 VARCHAR(30)     NOT NULL COMMENT 'SUPER_ADMIN|COMPANY_ADMIN|VENDOR_USER|EMPLOYEE',
    company_id           BIGINT UNSIGNED NULL COMMENT 'NULL only for SUPER_ADMIN',
    reference_id         BIGINT UNSIGNED NULL COMMENT 'employee_id or vendor_id depending on role',
    is_active            TINYINT(1)      NOT NULL DEFAULT 1,
    failed_login_count   TINYINT UNSIGNED NOT NULL DEFAULT 0,
    locked_until         DATETIME        NULL COMMENT 'Account locked until this time',
    last_login_at        DATETIME        NULL,
    must_change_password TINYINT(1)      NOT NULL DEFAULT 1,
    created_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by           BIGINT UNSIGNED NULL,
    updated_by           BIGINT UNSIGNED NULL,
    deleted_at           DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email),
    INDEX idx_users_role_company (role, company_id),
    INDEX idx_users_is_active (is_active),
    INDEX idx_users_deleted_at (deleted_at),
    CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='All user accounts across all tenants';

-- =============================================================================
-- AUTHENTICATION & AUDIT TABLES
-- =============================================================================

CREATE TABLE refresh_tokens (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED NOT NULL,
    token_hash   VARCHAR(255)    NOT NULL UNIQUE COMMENT 'SHA-256 hash of the raw token',
    expires_at   DATETIME        NOT NULL,
    revoked_at   DATETIME        NULL COMMENT 'NULL = still valid',
    created_ip   VARCHAR(45)     NULL,
    user_agent   VARCHAR(500)    NULL,
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_token_hash (token_hash),
    INDEX idx_refresh_token_user (user_id),
    INDEX idx_refresh_token_expires (expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='JWT Refresh Tokens - supports revocation';

CREATE TABLE audit_logs (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id   BIGINT UNSIGNED NULL COMMENT 'NULL for SUPER_ADMIN actions',
    user_id      BIGINT UNSIGNED NULL,
    username     VARCHAR(100)    NOT NULL COMMENT 'Snapshot at time of action',
    action       VARCHAR(50)     NOT NULL COMMENT 'CREATE|UPDATE|DELETE|LOGIN|LOGOUT|UPLOAD|DOWNLOAD',
    entity_type  VARCHAR(100)    NOT NULL,
    entity_id    BIGINT UNSIGNED NULL,
    old_value    JSON            NULL,
    new_value    JSON            NULL,
    description  VARCHAR(500)    NULL,
    ip_address   VARCHAR(45)     NULL,
    user_agent   VARCHAR(500)    NULL,
    status       VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS|FAILED',
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_audit_company (company_id),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Immutable audit log - never delete';

-- =============================================================================
-- ORGANIZATIONAL STRUCTURE TABLES
-- =============================================================================

CREATE TABLE departments (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id           BIGINT UNSIGNED NOT NULL,
    code                 VARCHAR(30)     NOT NULL,
    name                 VARCHAR(200)    NOT NULL,
    parent_dept_id       BIGINT UNSIGNED NULL COMMENT 'Self-reference for hierarchy',
    manager_employee_id  BIGINT UNSIGNED NULL,
    description          TEXT            NULL,
    display_order        INT             NOT NULL DEFAULT 0,
    is_active            TINYINT(1)      NOT NULL DEFAULT 1,
    created_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by           BIGINT UNSIGNED NULL,
    updated_by           BIGINT UNSIGNED NULL,
    deleted_at           DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_dept_company (company_id),
    INDEX idx_dept_deleted (deleted_at),
    CONSTRAINT fk_dept_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_dept_parent FOREIGN KEY (parent_dept_id) REFERENCES departments (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Hierarchical departments per company';

CREATE TABLE positions (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id   BIGINT UNSIGNED NOT NULL,
    code         VARCHAR(50)     NOT NULL,
    name         VARCHAR(200)    NOT NULL,
    level        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=lowest, 10=highest',
    description  TEXT            NULL,
    is_active    TINYINT(1)      NOT NULL DEFAULT 1,
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by   BIGINT UNSIGNED NULL,
    deleted_at   DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_position_company (company_id),
    CONSTRAINT fk_position_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Internal positions per company (differs from global job_roles)';

-- =============================================================================
-- VENDOR & CUSTOMER TABLES
-- =============================================================================

CREATE TABLE vendors (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id       BIGINT UNSIGNED NOT NULL,
    code             VARCHAR(30)     NOT NULL,
    name             VARCHAR(255)    NOT NULL,
    vendor_type      VARCHAR(20)     NOT NULL COMMENT 'PERSONNEL|EQUIPMENT|BOTH',
    contact_person   VARCHAR(200)    NULL,
    contact_email    VARCHAR(255)    NULL,
    contact_phone    VARCHAR(20)     NULL,
    address          VARCHAR(500)    NULL,
    tax_code         VARCHAR(20)     NULL,
    account_user_id  BIGINT UNSIGNED NULL COMMENT 'FK to users (VENDOR_USER account)',
    notes            TEXT            NULL,
    is_active        TINYINT(1)      NOT NULL DEFAULT 1,
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by       BIGINT UNSIGNED NULL,
    updated_by       BIGINT UNSIGNED NULL,
    deleted_at       DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_vendor_company (company_id),
    INDEX idx_vendor_type (vendor_type),
    INDEX idx_vendor_is_active (is_active),
    INDEX idx_vendor_deleted (deleted_at),
    CONSTRAINT fk_vendor_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vendor_user FOREIGN KEY (account_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Vendor/supplier per company';

CREATE TABLE vendor_contracts (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id             BIGINT UNSIGNED NOT NULL,
    vendor_id              BIGINT UNSIGNED NOT NULL,
    contract_number        VARCHAR(100)    NOT NULL,
    start_date             DATE            NOT NULL,
    end_date               DATE            NULL COMMENT 'NULL = indefinite contract',
    contract_value         DECIMAL(18, 2)  NULL,
    currency               VARCHAR(3)      NOT NULL DEFAULT 'VND',
    status                 VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|EXPIRED|TERMINATED|DRAFT',
    document_s3_key        VARCHAR(500)    NULL COMMENT 'PDF scan on S3',
    renewal_reminder_days  INT             NOT NULL DEFAULT 30,
    notes                  TEXT            NULL,
    created_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by             BIGINT UNSIGNED NULL,
    updated_by             BIGINT UNSIGNED NULL,
    deleted_at             DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_contract_company (company_id),
    INDEX idx_contract_vendor (vendor_id),
    INDEX idx_contract_end_date (end_date),
    INDEX idx_contract_status (status),
    CONSTRAINT fk_contract_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_contract_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Vendor contract lifecycle management';

CREATE TABLE customers (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id       BIGINT UNSIGNED NOT NULL,
    code             VARCHAR(30)     NOT NULL,
    vendor_code      VARCHAR(50)     NULL COMMENT 'Mã vendor do khách hàng cấp',
    name             VARCHAR(255)    NOT NULL,
    contact_person   VARCHAR(200)    NULL,
    contact_email    VARCHAR(255)    NULL,
    contact_phone    VARCHAR(20)     NULL,
    address          VARCHAR(500)    NULL,
    tax_code         VARCHAR(20)     NULL,
    notes            TEXT            NULL,
    is_active        TINYINT(1)      NOT NULL DEFAULT 1,
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by       BIGINT UNSIGNED NULL,
    updated_by       BIGINT UNSIGNED NULL,
    deleted_at       DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_customer_code (company_id, code),
    INDEX idx_customer_company (company_id),
    INDEX idx_customer_is_active (is_active),
    INDEX idx_customer_deleted (deleted_at),
    INDEX idx_customers_vendor_code (company_id, vendor_code),
    CONSTRAINT fk_customer_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Customer (client) per company';

-- =============================================================================
-- JOB ROLES & PRICING TABLES
-- =============================================================================

CREATE TABLE job_roles (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id       BIGINT UNSIGNED NOT NULL COMMENT 'Company that owns this job role',
    code             VARCHAR(30)     NOT NULL COMMENT 'SCREAMING_SNAKE_CASE: DEV_SR, TESTER',
    name             VARCHAR(200)    NOT NULL COMMENT 'Display name: Senior Developer',
    category         VARCHAR(50)     NOT NULL COMMENT 'DEVELOPMENT|QA|DEVOPS|MANAGEMENT|DESIGN|OTHER',
    seniority_level  VARCHAR(20)     NOT NULL DEFAULT 'MIDDLE' COMMENT 'INTERN|JUNIOR|MIDDLE|SENIOR|LEAD|PRINCIPAL',
    description      TEXT            NULL,
    skills_required  JSON            NULL COMMENT 'Array of skill names: ["Java","Spring Boot"]',
    is_active        TINYINT(1)      NOT NULL DEFAULT 1,
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by       BIGINT UNSIGNED NULL,
    updated_by       BIGINT UNSIGNED NULL,
    deleted_at       DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_job_role_code_company (company_id, code),
    INDEX idx_job_role_company (company_id),
    INDEX idx_job_role_category (category),
    INDEX idx_job_role_is_active (is_active),
    CONSTRAINT fk_job_role_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Job roles managed per company';

CREATE TABLE job_role_prices (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL COMMENT 'Price applies to this company only',
    job_role_id     BIGINT UNSIGNED NOT NULL,
    cost_price      DECIMAL(18, 2)  NOT NULL COMMENT 'Cost to hire from vendor (VND/month)',
    sell_price      DECIMAL(18, 2)  NOT NULL COMMENT 'Price to bill client (VND/month)',
    currency        VARCHAR(3)      NOT NULL DEFAULT 'VND',
    billing_unit    VARCHAR(20)     NOT NULL DEFAULT 'MONTHLY' COMMENT 'MONTHLY|DAILY|HOURLY',
    effective_from  DATE            NOT NULL COMMENT 'Price applies from this date',
    effective_to    DATE            NULL COMMENT 'NULL = currently active. Set when new price created.',
    notes           VARCHAR(500)    NULL COMMENT 'Reason for price change',
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      BIGINT UNSIGNED NULL COMMENT 'Immutable record - no updated_at/updated_by',
    PRIMARY KEY (id),
    INDEX idx_price_company (company_id),
    INDEX idx_price_job_role (job_role_id),
    INDEX idx_price_effective (company_id, job_role_id, effective_from, effective_to),
    CONSTRAINT fk_price_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_price_job_role FOREIGN KEY (job_role_id) REFERENCES job_roles (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Immutable price history per company per job role';

CREATE TABLE vendor_prices (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL,
    vendor_id       BIGINT UNSIGNED NOT NULL,
    job_role_id     BIGINT UNSIGNED NOT NULL,
    price           DECIMAL(18, 2)  NOT NULL COMMENT 'Unit price (VND)',
    price_unit      VARCHAR(10)     NOT NULL DEFAULT 'MONTHLY' COMMENT 'DAILY|MONTHLY',
    currency        VARCHAR(3)      NOT NULL DEFAULT 'VND',
    effective_from  DATE            NOT NULL,
    effective_to    DATE            NULL COMMENT 'NULL = currently active',
    notes           VARCHAR(500)    NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    INDEX idx_vp_company (company_id),
    INDEX idx_vp_vendor (vendor_id),
    INDEX idx_vp_effective (vendor_id, job_role_id, effective_from, effective_to),
    CONSTRAINT fk_vp_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vp_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vp_job_role FOREIGN KEY (job_role_id) REFERENCES job_roles (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Immutable vendor price history per job role';

CREATE TABLE customer_prices (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL,
    customer_id     BIGINT UNSIGNED NOT NULL,
    job_role_id     BIGINT UNSIGNED NOT NULL,
    price           DECIMAL(18, 2)  NOT NULL COMMENT 'Unit price (VND)',
    price_unit      VARCHAR(10)     NOT NULL DEFAULT 'MONTHLY' COMMENT 'DAILY|MONTHLY',
    currency        VARCHAR(3)      NOT NULL DEFAULT 'VND',
    effective_from  DATE            NOT NULL,
    effective_to    DATE            NULL COMMENT 'NULL = currently active',
    notes           VARCHAR(500)    NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    INDEX idx_cp_company (company_id),
    INDEX idx_cp_customer (customer_id),
    INDEX idx_cp_effective (customer_id, job_role_id, effective_from, effective_to),
    CONSTRAINT fk_cp_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cp_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cp_job_role FOREIGN KEY (job_role_id) REFERENCES job_roles (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Immutable customer price history per job role';

-- =============================================================================
-- EMPLOYEE TABLES
-- =============================================================================

CREATE TABLE employees (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id           VARCHAR(36)     NOT NULL UNIQUE COMMENT 'UUID for secure API exposure',
    company_id          BIGINT UNSIGNED NOT NULL,
    employee_code       VARCHAR(30)     NOT NULL,
    user_id             BIGINT UNSIGNED NULL UNIQUE COMMENT 'Login account - auto-created',
    full_name           VARCHAR(200)    NOT NULL,
    first_name          VARCHAR(100)    NOT NULL,
    last_name           VARCHAR(100)    NOT NULL,
    email_work          VARCHAR(255)    NULL,
    phone_work          VARCHAR(20)     NULL,
    avatar_s3_key       VARCHAR(500)    NULL,
    source_type         VARCHAR(20)     NOT NULL COMMENT 'INTERNAL|VENDOR',
    vendor_id           BIGINT UNSIGNED NULL COMMENT 'Required when source_type=VENDOR',
    job_role_id         BIGINT UNSIGNED NOT NULL,
    department_id       BIGINT UNSIGNED NULL,
    position_id         BIGINT UNSIGNED NULL,
    direct_manager_id   BIGINT UNSIGNED NULL,
    start_date          DATE            NOT NULL,
    end_date            DATE            NULL COMMENT 'NULL = currently active',
    status              VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|INACTIVE|ON_LEAVE|TERMINATED|PROBATION',
    employment_type     VARCHAR(20)     NOT NULL DEFAULT 'FULL_TIME' COMMENT 'FULL_TIME|PART_TIME|CONTRACT|INTERN',
    probation_end_date  DATE            NULL,
    date_of_birth       DATE            NULL,
    emergency_phone     VARCHAR(20)     NULL,
    notes               TEXT            NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by          BIGINT UNSIGNED NULL,
    updated_by          BIGINT UNSIGNED NULL,
    deleted_at          DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_employees_public_id (public_id),
    INDEX idx_emp_company (company_id),
    INDEX idx_emp_status (status),
    INDEX idx_emp_source (source_type),
    INDEX idx_emp_vendor (vendor_id),
    INDEX idx_emp_job_role (job_role_id),
    INDEX idx_emp_dept (department_id),
    INDEX idx_emp_full_name (full_name),
    INDEX idx_emp_deleted (deleted_at),
    INDEX idx_emp_company_status_jobrole (company_id, status, job_role_id),
    CONSTRAINT fk_emp_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_emp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_emp_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id) ON DELETE RESTRICT,
    CONSTRAINT fk_emp_job_role FOREIGN KEY (job_role_id) REFERENCES job_roles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_emp_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL,
    CONSTRAINT fk_emp_manager FOREIGN KEY (direct_manager_id) REFERENCES employees (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Main employee table - internal and vendor employees';

CREATE TABLE employee_details (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id             BIGINT UNSIGNED NOT NULL UNIQUE,
    dob                     DATE            NULL,
    gender                  VARCHAR(10)     NULL COMMENT 'MALE|FEMALE|OTHER',
    id_number               VARCHAR(20)     NULL COMMENT 'National ID',
    passport_no             VARCHAR(20)     NULL,
    personal_tax_code       VARCHAR(20)     NULL,
    nationality             VARCHAR(100)    NULL,
    permanent_address       VARCHAR(500)    NULL,
    current_address         VARCHAR(500)    NULL,
    bank_account_number     VARCHAR(50)     NULL,
    bank_name               VARCHAR(200)    NULL,
    bank_branch             VARCHAR(200)    NULL,
    emergency_contact_name  VARCHAR(200)    NULL,
    emergency_contact_phone VARCHAR(20)     NULL,
    emergency_contact_relation VARCHAR(100) NULL,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_emp_detail_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Sensitive employee details - separate table for access control';

CREATE TABLE employee_work_histories (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id     BIGINT UNSIGNED NOT NULL,
    employee_id    BIGINT UNSIGNED NOT NULL,
    job_role_id    BIGINT UNSIGNED NOT NULL,
    department_id  BIGINT UNSIGNED NULL,
    effective_from DATE            NOT NULL,
    effective_to   DATE            NULL COMMENT 'NULL = current assignment',
    notes          TEXT            NULL,
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by     BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    INDEX idx_history_company (company_id),
    INDEX idx_history_employee (employee_id),
    CONSTRAINT fk_history_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_history_job_role FOREIGN KEY (job_role_id) REFERENCES job_roles (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Audit trail for job role/department changes';

-- =============================================================================
-- DOCUMENT & FILE MANAGEMENT TABLES
-- =============================================================================

CREATE TABLE documents (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id        BIGINT UNSIGNED NOT NULL,
    employee_id       BIGINT UNSIGNED NOT NULL,
    document_type     VARCHAR(30)     NOT NULL COMMENT 'ID_CARD|PASSPORT|RESUME|DEGREE|CERTIFICATE|CONTRACT|OTHER',
    file_name         VARCHAR(255)    NOT NULL COMMENT 'Original file name',
    s3_key            VARCHAR(500)    NOT NULL COMMENT 'S3 object key: hrms/{company_id}/employees/{emp_id}/{type}/{uuid}.ext',
    onedrive_item_id  VARCHAR(500)    NULL COMMENT 'OneDrive item ID after sync',
    file_size         BIGINT          NOT NULL COMMENT 'File size in bytes',
    mime_type         VARCHAR(100)    NOT NULL,
    version           INT             NOT NULL DEFAULT 1,
    uploaded_by       BIGINT UNSIGNED NOT NULL,
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by        BIGINT UNSIGNED NULL,
    updated_by        BIGINT UNSIGNED NULL,
    deleted_at        DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_doc_company (company_id),
    INDEX idx_doc_employee (employee_id),
    INDEX idx_doc_type (document_type),
    CONSTRAINT fk_doc_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_doc_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Employee documents stored on S3 with optional OneDrive sync';

CREATE TABLE document_sync_sessions (
    id              BIGINT AUTO_INCREMENT,
    company_id      BIGINT       NOT NULL,
    triggered_by    BIGINT       NOT NULL COMMENT 'userId person who pressed button',
    started_at      DATETIME(6)  NOT NULL,
    completed_at    DATETIME(6)  NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING|COMPLETED|FAILED',
    total_documents INT          NOT NULL DEFAULT 0,
    synced_count    INT          NOT NULL DEFAULT 0,
    skipped_count   INT          NOT NULL DEFAULT 0,
    failed_count    INT          NOT NULL DEFAULT 0,
    target_type     VARCHAR(20)  NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL|S3_BUCKET',
    target_base_path VARCHAR(500) NOT NULL COMMENT 'Local path or s3://bucket/prefix',
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_sync_sessions_company (company_id),
    INDEX idx_sync_sessions_status (company_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='History of document synchronization operations';

CREATE TABLE document_sync_logs (
    id              BIGINT AUTO_INCREMENT,
    company_id      BIGINT       NOT NULL,
    session_id      BIGINT       NOT NULL,
    document_id     BIGINT       NOT NULL,
    employee_id     BIGINT       NOT NULL,
    employee_code   VARCHAR(50)  NOT NULL COMMENT 'Target folder name',
    s3_key          VARCHAR(500) NOT NULL,
    target_path     VARCHAR(500) NULL COMMENT 'File path after sync',
    sync_status     VARCHAR(20)  NOT NULL COMMENT 'SUCCESS|FAILED|SKIPPED',
    synced_at       DATETIME(6)  NOT NULL,
    error_message   VARCHAR(500) NULL,
    file_size       BIGINT       NULL,
    checksum        VARCHAR(64)  NULL COMMENT 'ETag from S3 (MD5 for small files)',
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_sync_logs_session (session_id),
    INDEX idx_sync_logs_document (document_id),
    INDEX idx_sync_logs_employee (employee_id),
    CONSTRAINT fk_sync_logs_session FOREIGN KEY (session_id) REFERENCES document_sync_sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Details of each document in synchronization operations';

-- =============================================================================
-- TIMESHEET TABLES
-- =============================================================================

CREATE TABLE timesheets (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id  BIGINT UNSIGNED NOT NULL,
    user_id     BIGINT UNSIGNED NOT NULL COMMENT 'User who logged the time',
    project_key VARCHAR(50)     NOT NULL COMMENT 'Project key e.g. TSHNWTS-5',
    project_id  VARCHAR(50)     NULL COMMENT 'Project ID cho báo cáo KH (nhân sự tự nhập)',
    work_date   DATE            NOT NULL COMMENT 'Date of work',
    hours       DECIMAL(4, 1)   NOT NULL COMMENT 'Hours logged (0.5 - 24)',
    ot_hours    DECIMAL(4, 1)   NOT NULL DEFAULT 0 COMMENT 'Overtime hours',
    locked_by_leave TINYINT(1)  NOT NULL DEFAULT 0 COMMENT 'Timesheet entry automatically created by leave',
    project_name VARCHAR(200)   NULL COMMENT 'Project name (display), e.g. TB-OMNI_CHANNEL',
    note        VARCHAR(255)    NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by  BIGINT UNSIGNED NULL,
    updated_by  BIGINT UNSIGNED NULL,
    deleted_at  DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_timesheet (user_id, project_key, work_date),
    INDEX idx_ts_company (company_id),
    INDEX idx_ts_user (user_id),
    INDEX idx_ts_date (work_date),
    INDEX idx_ts_project_id (project_id),
    CONSTRAINT fk_ts_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Daily timesheet entries per user per project';

CREATE TABLE timesheet_monthly (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id   BIGINT UNSIGNED NOT NULL,
    user_id      BIGINT UNSIGNED NOT NULL,
    `year_month`   VARCHAR(7)      NOT NULL COMMENT 'YYYY-MM',
    description  TEXT            NULL COMMENT 'Monthly notes/summary',
    additions    JSON            NULL COMMENT 'Custom field values keyed by field_key',
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_monthly (user_id, `year_month`),
    INDEX idx_monthly_company (company_id),
    CONSTRAINT fk_monthly_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_monthly_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Monthly timesheet metadata: description and custom field answers';

CREATE TABLE timesheet_attachments (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id   BIGINT UNSIGNED NOT NULL,
    user_id      BIGINT UNSIGNED NOT NULL,
    `year_month`   VARCHAR(7)      NOT NULL COMMENT 'YYYY-MM',
    file_name    VARCHAR(255)    NOT NULL,
    s3_key       VARCHAR(500)    NOT NULL,
    file_size    BIGINT          NOT NULL,
    mime_type    VARCHAR(100)    NOT NULL,
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by   BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    INDEX idx_attach_user_month (user_id, `year_month`),
    INDEX idx_attach_company (company_id),
    CONSTRAINT fk_attach_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_attach_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='File attachments (images/PDFs) for monthly timesheets';

CREATE TABLE company_timesheet_fields (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id  BIGINT UNSIGNED NOT NULL,
    field_name  VARCHAR(100)    NOT NULL COMMENT 'Display label',
    field_key   VARCHAR(50)     NOT NULL COMMENT 'Unique key for JSON storage',
    field_type  VARCHAR(20)     NOT NULL DEFAULT 'text' COMMENT 'text|number|checkbox',
    is_required TINYINT(1)      NOT NULL DEFAULT 0,
    sort_order  INT             NOT NULL DEFAULT 0,
    is_active   TINYINT(1)      NOT NULL DEFAULT 1,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_field_key (company_id, field_key),
    INDEX idx_field_company (company_id),
    CONSTRAINT fk_field_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Admin-configured custom fields shown on employee timesheet form';

CREATE TABLE timesheet_period_locks (
    id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    company_id                  BIGINT UNSIGNED NOT NULL,
    `year_month`                  VARCHAR(7)      NOT NULL,
    locked                      TINYINT(1)      NOT NULL DEFAULT 0,
    locked_by                   BIGINT UNSIGNED NULL,
    locked_at                   DATETIME        NULL,
    lock_reason                 VARCHAR(30)     NULL COMMENT 'MANUAL|PAYROLL_CONFIRMED|PAYROLL_EXPORTED|STATEMENT_SENT',
    payroll_lock_at             DATETIME        NULL COMMENT 'Time locked due to payroll report',
    statement_lock_at           DATETIME        NULL COMMENT 'Time locked due to statement sent',
    locked_by_report_id         BIGINT UNSIGNED NULL COMMENT 'ID PayrollRecord or Statement that triggered lock',
    accountant_confirmed        BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'Accountant confirmed timesheet',
    accountant_confirmed_by     BIGINT          NULL COMMENT 'User ID accountant confirmed by',
    accountant_confirmed_at     DATETIME        NULL COMMENT 'Time accountant confirmed',
    accountant_revert_reason    VARCHAR(500)    NULL COMMENT 'Reason for reverting accountant confirmation',
    statement_closed            BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'Statement closed - timesheet cannot be edited except by COMPANY_ADMIN',
    closed_by                   BIGINT UNSIGNED NULL COMMENT 'User who closed the statement',
    closed_at                   DATETIME(6)     NULL,
    closed_reason               VARCHAR(500)    NULL COMMENT 'Reason for closing',
    reopen_reason               VARCHAR(500)    NULL COMMENT 'Reason for reopening (by COMPANY_ADMIN)',
    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tpl_company_month UNIQUE (company_id, `year_month`),
    INDEX idx_tpl_company_month_locked (company_id, `year_month`, locked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- WORK CALENDAR TABLES
-- =============================================================================

CREATE TABLE work_calendars (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL,
    `year_month`    VARCHAR(7)      NOT NULL COMMENT 'YYYY-MM',
    total_workdays  INT             NOT NULL COMMENT 'Total working days in month',
    description     VARCHAR(255)    NULL,
    is_default      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Default calendar applied to multiple months',
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by      BIGINT UNSIGNED NULL,
    updated_by      BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_calendar_month (company_id, `year_month`),
    INDEX idx_cal_company (company_id),
    CONSTRAINT fk_cal_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Monthly work calendar created by admin';

CREATE TABLE work_calendar_details (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    calendar_id  BIGINT UNSIGNED NOT NULL,
    work_date    DATE            NOT NULL,
    day_type     VARCHAR(10)     NOT NULL DEFAULT 'WORKDAY' COMMENT 'WORKDAY|HOLIDAY|WEEKEND',
    note         VARCHAR(255)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cal_date (calendar_id, work_date),
    INDEX idx_cal_detail_calendar (calendar_id),
    INDEX idx_cal_detail_date (work_date),
    CONSTRAINT fk_cal_detail_calendar FOREIGN KEY (calendar_id) REFERENCES work_calendars (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Daily detail of work calendar (WORKDAY|HOLIDAY|WEEKEND)';

-- =============================================================================
-- LEAVE MANAGEMENT TABLES
-- =============================================================================

CREATE TABLE leave_types (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id  BIGINT UNSIGNED NOT NULL,
    code        VARCHAR(30)     NOT NULL COMMENT 'ANNUAL|SICK|UNPAID|MATERNITY|OTHER',
    name        VARCHAR(100)    NOT NULL COMMENT 'Display name: Annual Leave',
    max_days    INT             NULL COMMENT 'Max days/year. NULL = no limit',
    is_paid     TINYINT(1)      NOT NULL DEFAULT 1 COMMENT 'Does it include salary',
    is_active   TINYINT(1)      NOT NULL DEFAULT 1,
    auto_grant_on_create BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'TRUE = auto-grant quota when creating INTERNAL employee',
    grant_rounding VARCHAR(10) NOT NULL DEFAULT 'HALF_UP' COMMENT 'Rounding: FLOOR, CEIL, HALF_UP',
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_leave_type_code (company_id, code),
    INDEX idx_lt_company (company_id),
    CONSTRAINT fk_lt_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Leave type configuration per company';

CREATE TABLE leave_requests (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL,
    employee_id     BIGINT UNSIGNED NOT NULL,
    leave_type_id   BIGINT UNSIGNED NOT NULL,
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    total_days      DECIMAL(4,1)    NOT NULL COMMENT 'Number of days (can be 0.5)',
    is_half_day     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '0 = full day, 1 = half day (4h)',
    half_day_period ENUM('MORNING','AFTERNOON') NULL COMMENT 'Distinguish half day morning or afternoon',
    reason          VARCHAR(500)    NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|APPROVED|REJECTED|CANCELLED',
    approved_by     BIGINT UNSIGNED NULL COMMENT 'User ID of approver',
    approved_at     DATETIME(6)     NULL,
    reject_reason   VARCHAR(255)    NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by      BIGINT UNSIGNED NULL,
    updated_by      BIGINT UNSIGNED NULL,
    deleted_at      DATETIME(6)     NULL,
    PRIMARY KEY (id),
    INDEX idx_lr_company (company_id),
    INDEX idx_lr_employee (employee_id),
    INDEX idx_lr_dates (start_date, end_date),
    INDEX idx_lr_status (status),
    CONSTRAINT fk_lr_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lr_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lr_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id) ON DELETE RESTRICT,
    CONSTRAINT chk_half_day_period CHECK (is_half_day = 0 OR half_day_period IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Employee leave requests';

CREATE TABLE leave_balances (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL,
    employee_id     BIGINT UNSIGNED NOT NULL,
    leave_type_id   BIGINT UNSIGNED NOT NULL,
    year            SMALLINT        NOT NULL COMMENT 'Application year, e.g. 2025',
    total_quota     DECIMAL(5,1)    NOT NULL DEFAULT 0 COMMENT 'Total leave granted in year',
    used_days       DECIMAL(5,1)    NOT NULL DEFAULT 0 COMMENT 'Days used (from APPROVED leave_requests)',
    carried_over    DECIMAL(5,1)    NOT NULL DEFAULT 0 COMMENT 'Leave carried from previous year',
    expired_days    DECIMAL(5,1)    NOT NULL DEFAULT 0 COMMENT 'Unused leave that expired',
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_leave_balance (company_id, employee_id, leave_type_id, year),
    INDEX idx_lb_company (company_id),
    INDEX idx_lb_employee (employee_id),
    INDEX idx_lb_year (year),
    CONSTRAINT fk_lb_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lb_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lb_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Annual leave quota per employee per year';

-- =============================================================================
-- EMPLOYEE ASSIGNMENT & INTERNAL SALARY TABLES
-- =============================================================================

CREATE TABLE employee_assignments (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL,
    employee_id     BIGINT UNSIGNED NOT NULL,
    customer_id     BIGINT UNSIGNED NOT NULL COMMENT 'Customer using this employee',
    project_name    VARCHAR(200)    NULL COMMENT 'Project name (optional)',
    project_code    VARCHAR(50)     NULL COMMENT 'Project code',
    client_username VARCHAR(100)    NULL COMMENT 'Username at customer system (e.g. ps1.linhdn)',
    work_location   VARCHAR(50)     NULL COMMENT 'Work location (e.g. QT, TDH, THD)',
    start_date      DATE            NOT NULL,
    end_date        DATE            NULL COMMENT 'NULL = still effective',
    notes           VARCHAR(500)    NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by      BIGINT UNSIGNED NULL,
    updated_by      BIGINT UNSIGNED NULL,
    deleted_at      DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_ea_company (company_id),
    INDEX idx_ea_employee (employee_id),
    INDEX idx_ea_customer (customer_id),
    INDEX idx_ea_dates (start_date, end_date),
    CONSTRAINT fk_ea_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ea_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ea_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Employee-to-customer project assignment for billing';

CREATE TABLE internal_salaries (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL,
    employee_id     BIGINT UNSIGNED NOT NULL,
    monthly_salary  DECIMAL(18, 2)  NOT NULL COMMENT 'Gross monthly salary (VND)',
    effective_from  DATE            NOT NULL,
    effective_to    DATE            NULL COMMENT 'NULL = currently active',
    notes           VARCHAR(500)    NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    INDEX idx_salary_company (company_id),
    INDEX idx_salary_employee (employee_id),
    INDEX idx_salary_effective (employee_id, effective_from, effective_to),
    CONSTRAINT fk_salary_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_salary_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Immutable salary history for internal employees';

-- =============================================================================
-- BILLING TABLES
-- =============================================================================

CREATE TABLE billing_records (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    employee_id         BIGINT UNSIGNED NOT NULL,
    job_role_id         BIGINT UNSIGNED NOT NULL,
    billing_month       VARCHAR(7)      NOT NULL COMMENT 'YYYY-MM',
    period_start        DATE            NOT NULL COMMENT 'Period start date',
    period_end          DATE            NOT NULL COMMENT 'Period end date',
    source_type         VARCHAR(10)     NOT NULL COMMENT 'INTERNAL|VENDOR',
    vendor_id           BIGINT UNSIGNED NULL COMMENT 'NULL if internal employee',
    customer_id         BIGINT UNSIGNED NULL COMMENT 'Customer to bill',
    days_worked         DECIMAL(5, 1)   NOT NULL DEFAULT 0 COMMENT 'Actual working days',
    days_off            DECIMAL(5, 1)   NOT NULL DEFAULT 0 COMMENT 'Days off',
    paid_leave_days     DECIMAL(5,1)    NOT NULL DEFAULT 0 COMMENT 'Paid leave days (ANNUAL/SICK with is_paid=1)',
    unpaid_leave_days   DECIMAL(5,1)    NOT NULL DEFAULT 0 COMMENT 'Unpaid leave days (UNPAID or exceeded quota)',
    total_workdays      INT             NOT NULL DEFAULT 22 COMMENT 'Total workdays in month from calendar',
    price_unit          VARCHAR(10)     NOT NULL DEFAULT 'MONTHLY' COMMENT 'DAILY|MONTHLY',
    unit_cost_price     DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT 'Cost unit price (vendor/salary)',
    unit_sell_price     DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT 'Sell unit price (customer)',
    cost_amount         DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT 'Total cost amount',
    sell_amount         DECIMAL(18, 2)  NOT NULL DEFAULT 0 COMMENT 'Total revenue amount',
    currency            VARCHAR(3)      NOT NULL DEFAULT 'VND',
    calc_status         VARCHAR(20)     NOT NULL DEFAULT 'CALCULATED' COMMENT 'CALCULATED|ADJUSTED|LOCKED',
    calc_note           VARCHAR(500)    NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_billing_company_emp_period (company_id, employee_id, billing_month, period_start),
    INDEX idx_billing_company (company_id),
    INDEX idx_billing_employee (employee_id),
    INDEX idx_billing_month (billing_month),
    INDEX idx_billing_job_role (job_role_id),
    INDEX idx_billing_vendor (vendor_id),
    INDEX idx_billing_customer (customer_id),
    INDEX idx_billing_customer_month (company_id, customer_id, billing_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Monthly cost/revenue calculation per employee - period-based';

-- =============================================================================
-- PAYROLL TABLES
-- =============================================================================

CREATE TABLE payroll_records (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id              BIGINT UNSIGNED NOT NULL,
    employee_id             BIGINT UNSIGNED NOT NULL,
    billing_record_id       BIGINT UNSIGNED NULL COMMENT 'FK to billing_records',
    payroll_month           VARCHAR(7)      NOT NULL COMMENT 'YYYY-MM',
    base_salary             DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT 'Base salary from InternalSalary',
    days_worked             DECIMAL(5,1)    NOT NULL DEFAULT 0 COMMENT 'Actual working days from timesheet',
    days_off                DECIMAL(5,1)    NOT NULL DEFAULT 0,
    total_workdays          DECIMAL(5,1)    NOT NULL DEFAULT 22,
    calculated_salary       DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT 'Salary calculated by formula',
    bonus                   DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT 'Bonus entered by accountant',
    deduction               DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT 'Deduction (tax, insurance, etc)',
    insurance_salary        DECIMAL(18,2)   NULL COMMENT 'Insurance contribution base salary',
    insurance_employee_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT 'Employee insurance amount (10.5%)',
    insurance_employer_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT 'Employer insurance amount (21.5%)',
    bhxh_employee_amount    DECIMAL(18,2)   DEFAULT 0,
    bhxh_employer_amount    DECIMAL(18,2)   DEFAULT 0,
    bhyt_employee_amount    DECIMAL(18,2)   DEFAULT 0,
    bhyt_employer_amount    DECIMAL(18,2)   DEFAULT 0,
    bhtn_employee_amount    DECIMAL(18,2)   DEFAULT 0,
    bhtn_employer_amount    DECIMAL(18,2)   DEFAULT 0,
    adjustment_note         TEXT            NULL COMMENT 'Adjustment remarks',
    net_salary              DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '= calculated_salary + bonus - deduction',
    currency                VARCHAR(3)      NOT NULL DEFAULT 'VND',
    status                  VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|ADJUSTED|CONFIRMED|EXPORTED|STALE',
    confirmed_by            BIGINT UNSIGNED NULL COMMENT 'Accountant user ID',
    confirmed_at            DATETIME        NULL,
    exported_at             DATETIME        NULL,
    paid_by                 BIGINT UNSIGNED NULL,
    paid_at                 DATETIME(6)     NULL,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_payroll_employee_month (company_id, employee_id, payroll_month),
    INDEX idx_payroll_company_month (company_id, payroll_month),
    INDEX idx_payroll_status (status),
    CONSTRAINT fk_payroll_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Internal payroll monthly, allows accountant adjustments';

CREATE TABLE payroll_record_history (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payroll_record_id BIGINT UNSIGNED NOT NULL COMMENT 'FK to payroll_records',
    snapshot_data     JSON            NOT NULL COMMENT 'Full PayrollRecord snapshot at change time',
    change_type       VARCHAR(30)     NOT NULL COMMENT 'RESET_BY_ADMIN|TIMESHEET_MODIFIED|MANUAL_ADJUST',
    reason            TEXT            NULL COMMENT 'Reason for reset/change',
    old_file_s3_key   VARCHAR(500)    NULL COMMENT 'Link to old Excel/PDF file for reference',
    changed_by        BIGINT UNSIGNED NOT NULL,
    changed_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_history_payroll (payroll_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Payroll change history';

CREATE TABLE payroll_adjustments (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    payroll_record_id   BIGINT UNSIGNED NOT NULL,
    type                VARCHAR(20)     NOT NULL COMMENT 'BONUS, DEDUCTION, ALLOWANCE',
    description         VARCHAR(255)    NOT NULL COMMENT 'Description of adjustment',
    amount              DECIMAL(18,2)   NOT NULL COMMENT 'Amount',
    is_taxable          BOOLEAN         NOT NULL DEFAULT TRUE COMMENT 'Subject to personal income tax?',
    sort_order          INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_adjustment_payroll (payroll_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- STATEMENT TABLES
-- =============================================================================

CREATE TABLE statements (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    statement_type      VARCHAR(10)     NOT NULL COMMENT 'INTERNAL|CUSTOMER|VENDOR',
    recipient_id        BIGINT UNSIGNED NOT NULL COMMENT 'customer_id or vendor_id depending on type',
    statement_month     VARCHAR(7)      NOT NULL COMMENT 'YYYY-MM',
    total_employees     INT             NOT NULL DEFAULT 0,
    total_days_worked   DECIMAL(7,1)    NOT NULL DEFAULT 0,
    total_amount        DECIMAL(18,2)   NOT NULL DEFAULT 0,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'VND',
    status              VARCHAR(15)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|SENT|CONFIRMED|STALE|EXPORTED',
    file_s3_key         VARCHAR(500)    NULL COMMENT 'S3 key Excel/PDF file',
    sent_at             DATETIME        NULL,
    sent_by             BIGINT UNSIGNED NULL,
    confirmed_at        DATETIME        NULL COMMENT 'Time customer/vendor confirmed',
    confirmed_by        BIGINT UNSIGNED NULL COMMENT 'User ID of confirmer',
    exported_at         DATETIME        NULL COMMENT 'Time of last export',
    closed_at           DATETIME(6)     NULL COMMENT 'Time statement closed',
    closed_by           BIGINT UNSIGNED NULL COMMENT 'User ID who closed',
    stale_reason        VARCHAR(500)    NULL COMMENT 'Reason for STALE status (timesheet modified after export)',
    note                TEXT            NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_statement (company_id, statement_type, recipient_id, statement_month),
    INDEX idx_statement_company_month (company_id, statement_month),
    INDEX idx_statement_status (status),
    CONSTRAINT fk_statement_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Monthly confirmation statement per customer or vendor';

CREATE TABLE statement_line_items (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    statement_id        BIGINT UNSIGNED NOT NULL COMMENT 'FK to statements',
    employee_id         BIGINT UNSIGNED NOT NULL,
    billing_record_id   BIGINT UNSIGNED NULL COMMENT 'Source BillingRecord from timesheet',
    original_days       DECIMAL(5,1)    NOT NULL DEFAULT 0 COMMENT 'Original days from timesheet (immutable)',
    adjusted_days       DECIMAL(5,1)    NOT NULL DEFAULT 0 COMMENT 'Days after accountant override',
    original_hours      DECIMAL(7,1)    NOT NULL DEFAULT 0 COMMENT 'Original hours from timesheet (immutable)',
    adjusted_hours      DECIMAL(7,1)    NOT NULL DEFAULT 0 COMMENT 'Hours after accountant override',
    hours_per_day       DECIMAL(4,1)    NOT NULL DEFAULT 8.0 COMMENT 'Hours/day for conversion (typically 8h)',
    unit_sell_price     DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT 'Unit price to customer or vendor',
    original_amount     DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '= original_days x unit_sell_price',
    adjusted_amount     DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '= adjusted_days x unit_sell_price',
    note                TEXT            NULL COMMENT 'Reason for adjustment',
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_line_item (statement_id, employee_id),
    INDEX idx_line_item_statement (statement_id),
    INDEX idx_line_item_employee (employee_id),
    CONSTRAINT fk_line_item_statement FOREIGN KEY (statement_id) REFERENCES statements (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Detail per employee in statement';

CREATE TABLE statement_history (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    statement_id    BIGINT UNSIGNED NOT NULL COMMENT 'FK to statements',
    from_status     VARCHAR(15)     NULL COMMENT 'Previous status (NULL = new)',
    to_status       VARCHAR(15)     NOT NULL COMMENT 'New status',
    changed_by      BIGINT UNSIGNED NOT NULL COMMENT 'User ID who made change',
    changed_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    change_reason   VARCHAR(500)    NULL COMMENT 'Reason for change / remarks',
    snapshot_json   MEDIUMTEXT      NULL COMMENT 'JSON snapshot of statement at change time (audit)',
    PRIMARY KEY (id),
    INDEX idx_sh_statement (statement_id),
    INDEX idx_sh_changed_at (changed_at),
    CONSTRAINT fk_sh_statement FOREIGN KEY (statement_id) REFERENCES statements (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Statement status change history (audit trail)';

-- =============================================================================
-- INTERNAL STATEMENT TABLES
-- =============================================================================

CREATE TABLE internal_statement_entries (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT NOT NULL,
    employee_id     BIGINT NOT NULL,
    record_month    VARCHAR(7) NOT NULL,
    work_date       DATE NOT NULL,
    raw_hours       DECIMAL(4,1) NOT NULL DEFAULT 0.0,
    adjusted_hours  DECIMAL(4,1) NOT NULL DEFAULT 0.0,
    is_leave_day    BOOLEAN NOT NULL DEFAULT FALSE,
    is_half_leave   BOOLEAN NOT NULL DEFAULT FALSE,
    is_paid_leave   BOOLEAN NOT NULL DEFAULT FALSE,
    leave_type_name VARCHAR(100) NULL,
    note            VARCHAR(500) NULL,
    updated_by      BIGINT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_internal_entry (company_id, employee_id, work_date),
    INDEX idx_internal_month (company_id, record_month),
    INDEX idx_internal_emp_month (company_id, employee_id, record_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE internal_statement_summaries (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT NOT NULL,
    employee_id         BIGINT NOT NULL,
    record_month        VARCHAR(7) NOT NULL,
    total_raw_hours     DECIMAL(7,1) NOT NULL DEFAULT 0.0,
    total_adjusted_hours DECIMAL(7,1) NOT NULL DEFAULT 0.0,
    total_adjusted_days DECIMAL(6,2) NOT NULL DEFAULT 0.0,
    total_leave_days    INT NOT NULL DEFAULT 0,
    total_half_leave_days INT NOT NULL DEFAULT 0,
    status              VARCHAR(15) NOT NULL DEFAULT 'DRAFT',
    confirmed_by        BIGINT NULL,
    confirmed_at        DATETIME NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_internal_summary (company_id, employee_id, record_month),
    INDEX idx_internal_summary_month (company_id, record_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE statement_line_item_daily (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    statement_line_item_id  BIGINT NOT NULL,
    company_id              BIGINT NOT NULL,
    employee_id             BIGINT NOT NULL,
    work_date               DATE NOT NULL,
    internal_hours          DECIMAL(4,1) NOT NULL DEFAULT 0.0,
    customer_hours          DECIMAL(4,1) NOT NULL DEFAULT 0.0,
    timesheet_note          VARCHAR(500) NULL,
    accountant_note         VARCHAR(500) NULL,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_daily (statement_line_item_id, work_date),
    INDEX idx_customer_daily_emp (company_id, employee_id, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================================
-- FINANCIAL RECORDS TABLES
-- =============================================================================

CREATE TABLE monthly_financial_records (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    employee_id         BIGINT UNSIGNED NOT NULL,
    record_month        VARCHAR(7)      NOT NULL COMMENT 'YYYY-MM',
    customer_id         BIGINT UNSIGNED NULL COMMENT 'Customer this employee serves',
    source_type         VARCHAR(10)     NOT NULL COMMENT 'INTERNAL|VENDOR',
    vendor_id           BIGINT UNSIGNED NULL COMMENT 'NULL if INTERNAL',
    billed_days         DECIMAL(5,1)    NOT NULL DEFAULT 0 COMMENT 'From StatementLineItem.adjustedDays',
    unit_sell_price     DECIMAL(18,2)   NOT NULL DEFAULT 0,
    revenue_amount      DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '= billed_days x unit_sell_price',
    worked_days         DECIMAL(5,1)    NOT NULL DEFAULT 0,
    unit_cost_price     DECIMAL(18,2)   NOT NULL DEFAULT 0,
    base_cost_amount    DECIMAL(18,2)   NOT NULL DEFAULT 0,
    bonus_amount        DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT 'Only for INTERNAL',
    deduction_amount    DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT 'Only for INTERNAL',
    net_cost_amount     DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '= base_cost + bonus - deduction',
    profit_amount       DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '= revenue_amount - net_cost_amount',
    profit_margin       DECIMAL(10,2)   NOT NULL DEFAULT 0 COMMENT '% = profit / revenue x 100',
    status              VARCHAR(15)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|CONFIRMED',
    confirmed_at        DATETIME        NULL,
    confirmed_by        BIGINT UNSIGNED NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_financial_record (company_id, employee_id, record_month, customer_id),
    INDEX idx_financial_company_month (company_id, record_month),
    INDEX idx_financial_customer (customer_id),
    CONSTRAINT fk_financial_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Financial summary per employee per month for reporting';

-- =============================================================================
-- CONTRACT TABLES
-- =============================================================================

CREATE TABLE contract_types (
    id                          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id                  BIGINT UNSIGNED NOT NULL,
    code                        VARCHAR(50)     NOT NULL,
    name                        VARCHAR(200)    NOT NULL,
    category                    VARCHAR(20)     NOT NULL COMMENT 'CONTRACT | COMMITMENT',
    has_expiry                  BOOLEAN         NOT NULL DEFAULT TRUE,
    default_duration_months     INT             NULL,
    reminder_days_before        INT             NOT NULL DEFAULT 30,
    description                 VARCHAR(500)    NULL,
    is_active                   BOOLEAN         NOT NULL DEFAULT TRUE,
    sort_order                  INT             NOT NULL DEFAULT 0,
    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_contract_types_company_code (company_id, code),
    INDEX idx_contract_types_company_active (company_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE employee_contracts (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id           VARCHAR(36)     NOT NULL UNIQUE,
    company_id          BIGINT UNSIGNED NOT NULL,
    employee_id         BIGINT UNSIGNED NOT NULL,
    contract_type_id    BIGINT UNSIGNED NOT NULL,
    contract_number     VARCHAR(100)    NULL,
    signed_date         DATE            NULL,
    effective_from      DATE            NOT NULL,
    effective_to        DATE            NULL COMMENT 'NULL = indefinite / permanent',
    duration_months     INT             NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|EXPIRING_SOON|EXPIRED|TERMINATED',
    document_id         BIGINT UNSIGNED NULL COMMENT 'FK to documents (file scan)',
    notes               TEXT            NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          BIGINT UNSIGNED NULL,
    updated_by          BIGINT UNSIGNED NULL,
    deleted_at          DATETIME        NULL,
    UNIQUE KEY uq_employee_contracts_public_id (public_id),
    INDEX idx_ec_company_status (company_id, status),
    INDEX idx_ec_employee (company_id, employee_id),
    INDEX idx_ec_expiry (company_id, status, effective_to),
    INDEX idx_ec_type (company_id, contract_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================================
-- INSURANCE TABLES
-- =============================================================================

CREATE TABLE insurance_config (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT UNSIGNED NOT NULL,
    insurance_type  VARCHAR(20)     NOT NULL COMMENT 'BHXH, BHYT, BHTN',
    employee_rate   DECIMAL(5,2)    NOT NULL COMMENT 'Employee contribution rate (%)',
    employer_rate   DECIMAL(5,2)    NOT NULL COMMENT 'Employer contribution rate (%)',
    min_salary      DECIMAL(18,2)   NULL COMMENT 'Minimum salary for insurance contribution',
    max_salary      DECIMAL(18,2)   NULL COMMENT 'Maximum salary for insurance contribution',
    effective_from  DATE            NOT NULL COMMENT 'Date this config starts',
    effective_to    DATE            NULL COMMENT 'NULL = still in effect',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_insurance_config_company (company_id),
    UNIQUE KEY uq_insurance_config (company_id, insurance_type, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE insurance_policies (
    id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT UNSIGNED NOT NULL,
    employee_id             BIGINT UNSIGNED NOT NULL,
    insurance_salary        DECIMAL(18,2)   NOT NULL COMMENT 'Insurance contribution base salary',
    social_insurance_number VARCHAR(20)     NULL COMMENT 'Social insurance number',
    start_date              DATE            NOT NULL COMMENT 'Date employee joined insurance',
    end_date                DATE            NULL COMMENT 'NULL = currently insured',
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, SUSPENDED, CLOSED',
    notes                   TEXT            NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by              BIGINT UNSIGNED NULL,
    updated_by              BIGINT UNSIGNED NULL,
    deleted_at              TIMESTAMP       NULL,
    INDEX idx_insurance_policy_company (company_id),
    INDEX idx_insurance_policy_employee (employee_id),
    INDEX idx_insurance_policy_status (company_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE insurance_declarations (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT UNSIGNED NOT NULL,
    employee_id       BIGINT UNSIGNED NOT NULL,
    declaration_type  VARCHAR(20)     NOT NULL COMMENT 'INCREASE, DECREASE, ADJUSTMENT',
    effective_month   VARCHAR(7)      NOT NULL COMMENT 'YYYY-MM application month',
    deadline          DATE            NOT NULL COMMENT 'Document submission deadline',
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, SUBMITTED, APPROVED, REJECTED',
    old_salary        DECIMAL(18,2)   NULL COMMENT 'Old insurance salary',
    new_salary        DECIMAL(18,2)   NULL COMMENT 'New insurance salary',
    reason            VARCHAR(500)    NULL COMMENT 'Reason for increase/decrease',
    created_by        BIGINT UNSIGNED NULL,
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by        BIGINT UNSIGNED NULL,
    deleted_at        TIMESTAMP       NULL,
    INDEX idx_declaration_company (company_id),
    INDEX idx_declaration_employee (employee_id),
    INDEX idx_declaration_month (company_id, effective_month),
    INDEX idx_declaration_status (company_id, status),
    INDEX idx_declaration_deadline (company_id, deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE insurance_histories (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    insurance_policy_id BIGINT UNSIGNED NOT NULL,
    changed_field       VARCHAR(50)     NOT NULL COMMENT 'insurance_salary, status, etc',
    old_value           VARCHAR(255)    NULL,
    new_value           VARCHAR(255)    NULL,
    changed_by          BIGINT UNSIGNED NULL,
    changed_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_history_policy (insurance_policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- ASSET MANAGEMENT TABLES
-- =============================================================================

CREATE TABLE asset_categories (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id   BIGINT UNSIGNED NOT NULL,
    code         VARCHAR(30)     NOT NULL,
    name         VARCHAR(100)    NOT NULL,
    description  VARCHAR(500)    NULL,
    is_active    BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by   BIGINT UNSIGNED NULL,
    updated_by   BIGINT UNSIGNED NULL,
    deleted_at   DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_asset_cat_company (company_id),
    INDEX idx_asset_cat_code (code),
    INDEX idx_asset_cat_deleted (deleted_at),
    UNIQUE INDEX uq_asset_cat_code_company (code, company_id),
    CONSTRAINT fk_asset_cat_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assets (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id         BIGINT UNSIGNED NOT NULL,
    asset_code         VARCHAR(30)     NOT NULL,
    name               VARCHAR(200)    NOT NULL,
    serial_number      VARCHAR(100)    NULL,
    label_code         VARCHAR(50)     NULL COMMENT 'Label code on device for identification',
    description        TEXT            NULL COMMENT 'Device configuration: CPU, RAM, SSD, monitor',
    category_id        BIGINT UNSIGNED NOT NULL,
    ownership_type     VARCHAR(20)     NOT NULL DEFAULT 'COMPANY_OWNED' COMMENT 'COMPANY_OWNED|VENDOR_OWNED',
    owner_vendor_id    BIGINT UNSIGNED NULL COMMENT 'NULL=company owned, set=vendor owned',
    supplier_vendor_id BIGINT UNSIGNED NULL COMMENT 'Vendor that supplied this asset',
    is_rented          BOOLEAN         NOT NULL DEFAULT FALSE,
    rental_notes       TEXT            NULL,
    purchase_price     DECIMAL(15,2)   NULL,
    purchase_date      DATE            NULL,
    warranty_months    INT             NULL,
    status             VARCHAR(20)     NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE|IN_USE|MAINTENANCE|DISPOSED|LOST',
    asset_condition    VARCHAR(20)     NOT NULL DEFAULT 'NEW' COMMENT 'NEW|GOOD|FAIR|POOR',
    notes              TEXT            NULL,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by         BIGINT UNSIGNED NULL,
    updated_by         BIGINT UNSIGNED NULL,
    deleted_at         DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_asset_company (company_id),
    INDEX idx_asset_category (category_id),
    INDEX idx_asset_status (status),
    INDEX idx_asset_ownership (ownership_type),
    INDEX idx_asset_owner_vendor (owner_vendor_id),
    INDEX idx_asset_deleted (deleted_at),
    INDEX idx_asset_label_code (label_code),
    UNIQUE INDEX uq_asset_code_company (asset_code, company_id),
    CONSTRAINT fk_asset_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_asset_category FOREIGN KEY (category_id) REFERENCES asset_categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_asset_owner_vendor FOREIGN KEY (owner_vendor_id) REFERENCES vendors (id) ON DELETE RESTRICT,
    CONSTRAINT fk_asset_supplier_vendor FOREIGN KEY (supplier_vendor_id) REFERENCES vendors (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE asset_assignments (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id             BIGINT UNSIGNED NOT NULL,
    asset_id               BIGINT UNSIGNED NOT NULL,
    employee_id            BIGINT UNSIGNED NOT NULL,
    assigned_date          DATE            NOT NULL,
    returned_date          DATE            NULL COMMENT 'NULL = currently holding',
    assigned_by            BIGINT UNSIGNED NOT NULL COMMENT 'User who approved',
    condition_on_assign    VARCHAR(20)     NOT NULL COMMENT 'NEW|GOOD|FAIR|POOR',
    condition_on_return    VARCHAR(20)     NULL,
    notes                  TEXT            NULL,
    monthly_rental_price   DECIMAL(15,2)   NULL COMMENT 'Monthly rental fee (VND) for vendor employees',
    created_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by             BIGINT UNSIGNED NULL,
    updated_by             BIGINT UNSIGNED NULL,
    deleted_at             DATETIME(6)     NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_assign_company (company_id),
    INDEX idx_assign_asset (asset_id),
    INDEX idx_assign_employee (employee_id),
    INDEX idx_assign_active (asset_id, returned_date),
    CONSTRAINT fk_assign_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_assign_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_assign_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_assign_by FOREIGN KEY (assigned_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- NOTIFICATION & ALERT TABLES
-- =============================================================================

CREATE TABLE notification_logs (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT UNSIGNED NOT NULL,
    recipient_email VARCHAR(255)    NOT NULL,
    subject         VARCHAR(500)    NOT NULL,
    template_type   VARCHAR(50)     NOT NULL COMMENT 'INSURANCE_DEADLINE, CONTRACT_EXPIRY, PAYROLL_CONFIRMED',
    reference_type  VARCHAR(50)     NULL COMMENT 'INSURANCE_DECLARATION, CONTRACT, PAYROLL',
    reference_id    BIGINT UNSIGNED NULL COMMENT 'FK to related record',
    status          VARCHAR(20)     NOT NULL DEFAULT 'SENT' COMMENT 'SENT, FAILED, PENDING',
    error_message   TEXT            NULL,
    sent_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notif_company (company_id),
    INDEX idx_notif_template (company_id, template_type),
    INDEX idx_notif_sent (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_settings (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT UNSIGNED NOT NULL,
    template_type        VARCHAR(50)     NOT NULL COMMENT 'Type of notification',
    enabled              BOOLEAN         NOT NULL DEFAULT TRUE,
    recipient_emails     TEXT            NULL COMMENT 'Comma-separated list of emails',
    days_before_deadline INT             NOT NULL DEFAULT 7 COMMENT 'Days before deadline to remind',
    created_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_notif_setting (company_id, template_type),
    INDEX idx_notif_setting_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE alerts (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT UNSIGNED NOT NULL,
    alert_key       VARCHAR(100)    NOT NULL COMMENT 'Unique key: {CATEGORY}:{REF_TYPE}:{REF_ID}',
    category        VARCHAR(30)     NOT NULL COMMENT 'INSURANCE, CONTRACT, LEAVE, PAYROLL, PROBATION',
    severity        VARCHAR(10)     NOT NULL COMMENT 'URGENT, WARNING, INFO',
    title           VARCHAR(255)    NOT NULL COMMENT 'Short title',
    description     TEXT            NULL COMMENT 'Details',
    reference_type  VARCHAR(50)     NULL COMMENT 'DECLARATION, CONTRACT, EMPLOYEE',
    reference_id    BIGINT UNSIGNED NULL COMMENT 'ID of original record',
    action_url      VARCHAR(255)    NULL COMMENT 'Action link: /insurance, /contracts/5',
    due_date        DATE            NULL COMMENT 'Deadline for sorting and highlighting overdue',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, DISMISSED, RESOLVED',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    dismissed_at    TIMESTAMP       NULL,
    dismissed_by    BIGINT UNSIGNED NULL,
    UNIQUE KEY uq_alert_key (company_id, alert_key),
    INDEX idx_alert_company_status (company_id, status),
    INDEX idx_alert_category (company_id, category, status),
    INDEX idx_alert_due (company_id, due_date),
    INDEX idx_alert_severity (company_id, severity, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- COMPANY SETTINGS TABLE
-- =============================================================================

CREATE TABLE company_settings (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id   BIGINT UNSIGNED NOT NULL UNIQUE,
    settings     JSON            NOT NULL COMMENT 'Flexible JSON config per company',
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_settings_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Per-company configuration settings';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- SEED DATA
-- =============================================================================

-- Superadmin user (V2 + V48)
INSERT INTO users (username, email, password_hash, role, company_id, is_active, must_change_password)
SELECT 'superadmin', 'admin@hrms.com',
       '$2a$12$HDEzBZCyPhmcP2sClvLcc.QBPvOHCedOcDqpN/IEOdBJSkiqFRaGO',
       'SUPER_ADMIN', NULL, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'superadmin' AND role = 'SUPER_ADMIN'
);

-- Contract types per company (V39 - using CROSS JOIN pattern)
INSERT INTO contract_types (company_id, code, name, category, has_expiry, default_duration_months, reminder_days_before, is_active)
SELECT c.id, 'LABOR_INDEFINITE', 'Hợp đồng không thời hạn', 'CONTRACT', FALSE, NULL, 30, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM contract_types ct WHERE ct.company_id = c.id AND ct.code = 'LABOR_INDEFINITE'
);

INSERT INTO contract_types (company_id, code, name, category, has_expiry, default_duration_months, reminder_days_before, is_active)
SELECT c.id, 'LABOR_DEFINITE', 'Hợp đồng có thời hạn', 'CONTRACT', TRUE, 12, 30, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM contract_types ct WHERE ct.company_id = c.id AND ct.code = 'LABOR_DEFINITE'
);

INSERT INTO contract_types (company_id, code, name, category, has_expiry, default_duration_months, reminder_days_before, is_active)
SELECT c.id, 'PROBATION', 'Hợp đồng thử việc', 'CONTRACT', TRUE, 2, 30, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM contract_types ct WHERE ct.company_id = c.id AND ct.code = 'PROBATION'
);

INSERT INTO contract_types (company_id, code, name, category, has_expiry, default_duration_months, reminder_days_before, is_active)
SELECT c.id, 'SEASONAL', 'Hợp đồng thời vụ', 'CONTRACT', TRUE, 6, 30, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM contract_types ct WHERE ct.company_id = c.id AND ct.code = 'SEASONAL'
);

INSERT INTO contract_types (company_id, code, name, category, has_expiry, default_duration_months, reminder_days_before, is_active)
SELECT c.id, 'SECURITY_COMMITMENT', 'Cam kết an toàn bảo mật', 'COMMITMENT', FALSE, NULL, 30, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM contract_types ct WHERE ct.company_id = c.id AND ct.code = 'SECURITY_COMMITMENT'
);

INSERT INTO contract_types (company_id, code, name, category, has_expiry, default_duration_months, reminder_days_before, is_active)
SELECT c.id, 'REGULATION_COMMITMENT', 'Cam kết nội quy công ty', 'COMMITMENT', FALSE, NULL, 30, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM contract_types ct WHERE ct.company_id = c.id AND ct.code = 'REGULATION_COMMITMENT'
);

-- Leave types per company (9 types)
INSERT INTO leave_types (company_id, code, name, max_days, is_paid, is_active, auto_grant_on_create, grant_rounding)
SELECT c.id, 'ANNUAL', 'Nghỉ phép năm', 12, TRUE, TRUE, TRUE, 'HALF_UP'
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM leave_types lt WHERE lt.company_id = c.id AND lt.code = 'ANNUAL'
);

INSERT INTO leave_types (company_id, code, name, max_days, is_paid, is_active)
SELECT c.id, 'SICK', 'Nghỉ ốm', 30, TRUE, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM leave_types lt WHERE lt.company_id = c.id AND lt.code = 'SICK'
);

INSERT INTO leave_types (company_id, code, name, max_days, is_paid, is_active)
SELECT c.id, 'UNPAID', 'Nghỉ không lương', NULL, FALSE, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM leave_types lt WHERE lt.company_id = c.id AND lt.code = 'UNPAID'
);

INSERT INTO leave_types (company_id, code, name, max_days, is_paid, is_active)
SELECT c.id, 'MATERNITY', 'Nghỉ sinh con', 180, TRUE, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM leave_types lt WHERE lt.company_id = c.id AND lt.code = 'MATERNITY'
);

INSERT INTO leave_types (company_id, code, name, max_days, is_paid, is_active)
SELECT c.id, 'PATERNITY', 'Nghỉ hưởng chế độ', 14, TRUE, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM leave_types lt WHERE lt.company_id = c.id AND lt.code = 'PATERNITY'
);

INSERT INTO leave_types (company_id, code, name, max_days, is_paid, is_active)
SELECT c.id, 'WEDDING', 'Nghỉ kết hôn', 3, TRUE, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM leave_types lt WHERE lt.company_id = c.id AND lt.code = 'WEDDING'
);

INSERT INTO leave_types (company_id, code, name, max_days, is_paid, is_active)
SELECT c.id, 'CHILD_WEDDING', 'Nghỉ con kết hôn', 1, TRUE, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM leave_types lt WHERE lt.company_id = c.id AND lt.code = 'CHILD_WEDDING'
);

INSERT INTO leave_types (company_id, code, name, max_days, is_paid, is_active)
SELECT c.id, 'FUNERAL', 'Nghỉ tang lễ', 3, TRUE, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM leave_types lt WHERE lt.company_id = c.id AND lt.code = 'FUNERAL'
);

INSERT INTO leave_types (company_id, code, name, max_days, is_paid, is_active)
SELECT c.id, 'OTHER', 'Nghỉ khác', NULL, FALSE, TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM leave_types lt WHERE lt.company_id = c.id AND lt.code = 'OTHER'
);

-- Insurance config per company (BHXH 8%/17.5%, BHYT 1.5%/3%, BHTN 1%/1%, min_salary=4960000, max_salary=46800000)
INSERT INTO insurance_config (company_id, insurance_type, employee_rate, employer_rate, min_salary, max_salary, effective_from)
SELECT c.id, 'BHXH', 8.0, 17.5, 4960000, 46800000, CURDATE()
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM insurance_config ic WHERE ic.company_id = c.id AND ic.insurance_type = 'BHXH' AND ic.effective_to IS NULL
);

INSERT INTO insurance_config (company_id, insurance_type, employee_rate, employer_rate, min_salary, max_salary, effective_from)
SELECT c.id, 'BHYT', 1.5, 3.0, 4960000, 46800000, CURDATE()
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM insurance_config ic WHERE ic.company_id = c.id AND ic.insurance_type = 'BHYT' AND ic.effective_to IS NULL
);

INSERT INTO insurance_config (company_id, insurance_type, employee_rate, employer_rate, min_salary, max_salary, effective_from)
SELECT c.id, 'BHTN', 1.0, 1.0, 4960000, 46800000, CURDATE()
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM insurance_config ic WHERE ic.company_id = c.id AND ic.insurance_type = 'BHTN' AND ic.effective_to IS NULL
);

-- Job roles per company (13 default roles)
INSERT INTO job_roles (company_id, code, name, category, seniority_level, description, skills_required, is_active)
SELECT c.id, 'DEV_INTERN', 'Intern Developer', 'DEVELOPMENT', 'INTERN', 'Sinh viên thực tập / Fresh graduate', '["HTML","CSS","JavaScript"]', 1
FROM companies c WHERE c.deleted_at IS NULL AND NOT EXISTS (
    SELECT 1 FROM job_roles jr WHERE jr.company_id = c.id AND jr.code = 'DEV_INTERN' AND jr.deleted_at IS NULL
);

INSERT INTO job_roles (company_id, code, name, category, seniority_level, description, skills_required, is_active)
SELECT c.id, 'DEV_JR', 'Junior Developer', 'DEVELOPMENT', 'JUNIOR', '0-2 năm kinh nghiệm', '["Java","JavaScript","SQL"]', 1
FROM companies c WHERE c.deleted_at IS NULL AND NOT EXISTS (
    SELECT 1 FROM job_roles jr WHERE jr.company_id = c.id AND jr.code = 'DEV_JR' AND jr.deleted_at IS NULL
);

INSERT INTO job_roles (company_id, code, name, category, seniority_level, description, skills_required, is_active)
SELECT c.id, 'DEV_MID', 'Middle Developer', 'DEVELOPMENT', 'MIDDLE', '2-4 năm kinh nghiệm', '["Java","Spring Boot","React","SQL"]', 1
FROM companies c WHERE c.deleted_at IS NULL AND NOT EXISTS (
    SELECT 1 FROM job_roles jr WHERE jr.company_id = c.id AND jr.code = 'DEV_MID' AND jr.deleted_at IS NULL
);

INSERT INTO job_roles (company_id, code, name, category, seniority_level, description, skills_required, is_active)
SELECT c.id, 'DEV_SR', 'Senior Developer', 'DEVELOPMENT', 'SENIOR', '4+ năm kinh nghiệm', '["Java","Spring Boot","Microservices","AWS"]', 1
FROM companies c WHERE c.deleted_at IS NULL AND NOT EXISTS (
    SELECT 1 FROM job_roles jr WHERE jr.company_id = c.id AND jr.code = 'DEV_SR' AND jr.deleted_at IS NULL
);

INSERT INTO job_roles (company_id, code, name, category, seniority_level, description, skills_required, is_active)
SELECT c.id, 'DEV_LEAD', 'Tech Lead', 'DEVELOPMENT', 'LEAD', 'Kỹ thuật trưởng', '["Architecture","Java","Mentoring","CI/CD"]', 1
FROM companies c WHERE c.deleted_at IS NULL AND NOT EXISTS (
    SELECT 1 FROM job_roles jr WHERE jr.company_id = c.id AND jr.code = 'DEV_LEAD' AND jr.deleted_at IS NULL
);

INSERT INTO job_roles (company_id, code, name, category, seniority_level, description, skills_required, is_active)
SELECT c.id, 'QA_JR', 'Junior QA Tester', 'QA', 'JUNIOR', 'Kiểm thử thủ công', '["Manual Testing","Test Cases","Bug Reporting"]', 1
FROM companies c WHERE c.deleted_at IS NULL AND NOT EXISTS (
    SELECT 1 FROM job_roles jr WHERE jr.company_id = c.id AND jr.code = 'QA_JR' AND jr.deleted_at IS NULL
);

INSERT INTO job_roles (company_id, code, name, category, seniority_level, description, skills_required, is_active)
SELECT c.id, 'QA_MID', 'QA Engineer', 'QA', 'MIDDLE', 'Kiểm thử tự động', '["Selenium","JMeter","Postman","SQL"]', 1
FROM companies c WHERE c.deleted_at IS NULL AND NOT EXISTS (
    SELECT 1 FROM job_roles jr WHERE jr.company_id = c.id AND jr.code = 'QA_MID' AND jr.deleted_at IS NULL
);

INSERT INTO job_roles (company_id, code, name, category, seniority_level, description, skills_required, is_active)
SELECT c.id, 'QA_SR', 'Senior QA Engineer', 'QA', 'SENIOR', 'Chiến lược kiểm thử', '["Test Strategy","Performance Testing","CI/CD"]', 1
FROM companies c WHERE c.deleted_at IS NULL AND NOT EXISTS (
    SELECT 1 FROM job_roles jr WHERE jr.company_id = c.id AND jr.code = 'QA_SR' AND jr.deleted_at IS NULL
);

INSERT INTO job_roles (company_id, code, name, category, seniority_level, description, skills_required, is_active)
SELECT c.id, 'DEVOPS_MID', 'DevOps Engineer', 'DEVOPS', 'MIDDLE', 'CI/CD và hạ tầng', '["Docker","Kubernetes","AWS","Jenkins"]', 1
FROM companies c WHERE c.deleted_at IS NULL AND NOT EXISTS (
    SELECT 1 FROM job_roles jr WHERE jr.company_id = c.id AND jr.code = 'DEVOPS_MID' AND jr.deleted_at IS NULL
);

INSERT INTO job_roles (company_id, code, name, category, seniority_level, description, skills_required, is_active)
SELECT c.id, 'DEVOPS_SR', 'Senior DevOps Engineer', 'DEVOPS', 'SENIOR', 'Kiến trúc Cloud', '["AWS","Terraform","Kubernetes","Security"]', 1
FROM companies c WHERE c.deleted_at IS NULL AND NOT EXISTS (
    SELECT 1 FROM job_roles jr WHERE jr.company_id = c.id AND jr.code = 'DEVOPS_SR' AND jr.deleted_at IS NULL
);

INSERT INTO job_roles (company_id, code, name, category, seniority_level, description, skills_required, is_active)
SELECT c.id, 'BA_MID', 'Business Analyst', 'MANAGEMENT', 'MIDDLE', 'Phân tích yêu cầu', '["Requirements","UML","SQL","Agile"]', 1
FROM companies c WHERE c.deleted_at IS NULL AND NOT EXISTS (
    SELECT 1 FROM job_roles jr WHERE jr.company_id = c.id AND jr.code = 'BA_MID' AND jr.deleted_at IS NULL
);

INSERT INTO job_roles (company_id, code, name, category, seniority_level, description, skills_required, is_active)
SELECT c.id, 'PM_SR', 'Project Manager', 'MANAGEMENT', 'SENIOR', 'Quản lý dự án', '["PMP","Agile","Scrum","Risk Management"]', 1
FROM companies c WHERE c.deleted_at IS NULL AND NOT EXISTS (
    SELECT 1 FROM job_roles jr WHERE jr.company_id = c.id AND jr.code = 'PM_SR' AND jr.deleted_at IS NULL
);

INSERT INTO job_roles (company_id, code, name, category, seniority_level, description, skills_required, is_active)
SELECT c.id, 'UI_MID', 'UI/UX Designer', 'DESIGN', 'MIDDLE', 'Thiết kế giao diện', '["Figma","Adobe XD","UX Research"]', 1
FROM companies c WHERE c.deleted_at IS NULL AND NOT EXISTS (
    SELECT 1 FROM job_roles jr WHERE jr.company_id = c.id AND jr.code = 'UI_MID' AND jr.deleted_at IS NULL
);

-- Asset categories per company (9 types)
INSERT INTO asset_categories (company_id, code, name, is_active)
SELECT c.id, 'LAPTOP', 'Laptop', TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM asset_categories ac WHERE ac.company_id = c.id AND ac.code = 'LAPTOP'
);

INSERT INTO asset_categories (company_id, code, name, is_active)
SELECT c.id, 'DESKTOP', 'Desktop', TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM asset_categories ac WHERE ac.company_id = c.id AND ac.code = 'DESKTOP'
);

INSERT INTO asset_categories (company_id, code, name, is_active)
SELECT c.id, 'MONITOR', 'Monitor', TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM asset_categories ac WHERE ac.company_id = c.id AND ac.code = 'MONITOR'
);

INSERT INTO asset_categories (company_id, code, name, is_active)
SELECT c.id, 'PHONE', 'Phone', TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM asset_categories ac WHERE ac.company_id = c.id AND ac.code = 'PHONE'
);

INSERT INTO asset_categories (company_id, code, name, is_active)
SELECT c.id, 'PERIPHERAL', 'Peripheral', TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM asset_categories ac WHERE ac.company_id = c.id AND ac.code = 'PERIPHERAL'
);

INSERT INTO asset_categories (company_id, code, name, is_active)
SELECT c.id, 'NETWORK', 'Network', TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM asset_categories ac WHERE ac.company_id = c.id AND ac.code = 'NETWORK'
);

INSERT INTO asset_categories (company_id, code, name, is_active)
SELECT c.id, 'FURNITURE', 'Furniture', TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM asset_categories ac WHERE ac.company_id = c.id AND ac.code = 'FURNITURE'
);

INSERT INTO asset_categories (company_id, code, name, is_active)
SELECT c.id, 'SOFTWARE', 'Software', TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM asset_categories ac WHERE ac.company_id = c.id AND ac.code = 'SOFTWARE'
);

INSERT INTO asset_categories (company_id, code, name, is_active)
SELECT c.id, 'OTHER', 'Other', TRUE
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM asset_categories ac WHERE ac.company_id = c.id AND ac.code = 'OTHER'
);

-- Notification settings per company
INSERT INTO notification_settings (company_id, template_type, enabled, days_before_deadline)
SELECT c.id, 'CONTRACT_EXPIRY', TRUE, 30
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM notification_settings ns WHERE ns.company_id = c.id AND ns.template_type = 'CONTRACT_EXPIRY'
);

INSERT INTO notification_settings (company_id, template_type, enabled, days_before_deadline)
SELECT c.id, 'INSURANCE_DEADLINE', TRUE, 14
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM notification_settings ns WHERE ns.company_id = c.id AND ns.template_type = 'INSURANCE_DEADLINE'
);

INSERT INTO notification_settings (company_id, template_type, enabled, days_before_deadline)
SELECT c.id, 'PROBATION_ENDING', TRUE, 14
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM notification_settings ns WHERE ns.company_id = c.id AND ns.template_type = 'PROBATION_ENDING'
);

INSERT INTO notification_settings (company_id, template_type, enabled, days_before_deadline)
SELECT c.id, 'PAYROLL_CONFIRMED', TRUE, 0
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM notification_settings ns WHERE ns.company_id = c.id AND ns.template_type = 'PAYROLL_CONFIRMED'
);

INSERT INTO notification_settings (company_id, template_type, enabled, days_before_deadline)
SELECT c.id, 'LEAVE_REQUEST', TRUE, 0
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM notification_settings ns WHERE ns.company_id = c.id AND ns.template_type = 'LEAVE_REQUEST'
);

-- Company settings per company
INSERT INTO company_settings (company_id, settings)
SELECT c.id, JSON_OBJECT(
    'timezone', 'Asia/Ho_Chi_Minh',
    'locale', 'vi-VN',
    'currency', 'VND',
    'workHoursPerDay', 8,
    'standardWorkdays', 22
)
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM company_settings cs WHERE cs.company_id = c.id
);
