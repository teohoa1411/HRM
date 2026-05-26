CREATE TABLE IF NOT EXISTS timesheet_monthly (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id  BIGINT UNSIGNED NOT NULL,
    user_id     BIGINT UNSIGNED NOT NULL,
    `year_month` VARCHAR(7)     NOT NULL,
    description TEXT            NULL,
    additions   JSON            NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_monthly (user_id, `year_month`),
    INDEX idx_monthly_company (company_id),
    CONSTRAINT fk_monthly_user    FOREIGN KEY (user_id)    REFERENCES users (id)     ON DELETE CASCADE,
    CONSTRAINT fk_monthly_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS timesheet_attachments (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id  BIGINT UNSIGNED NOT NULL,
    user_id     BIGINT UNSIGNED NOT NULL,
    `year_month` VARCHAR(7)     NOT NULL,
    file_name   VARCHAR(255)    NOT NULL,
    s3_key      VARCHAR(500)    NOT NULL,
    file_size   BIGINT          NOT NULL,
    mime_type   VARCHAR(100)    NOT NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by  BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    INDEX idx_attach_user_month (user_id, `year_month`),
    INDEX idx_attach_company (company_id),
    CONSTRAINT fk_attach_user    FOREIGN KEY (user_id)    REFERENCES users (id)     ON DELETE CASCADE,
    CONSTRAINT fk_attach_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS company_timesheet_fields (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id  BIGINT UNSIGNED NOT NULL,
    field_name  VARCHAR(100)    NOT NULL,
    field_key   VARCHAR(50)     NOT NULL,
    field_type  VARCHAR(20)     NOT NULL DEFAULT 'text',
    is_required TINYINT(1)      NOT NULL DEFAULT 0,
    sort_order  INT             NOT NULL DEFAULT 0,
    is_active   TINYINT(1)      NOT NULL DEFAULT 1,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_field_key (company_id, field_key),
    INDEX idx_field_company (company_id),
    CONSTRAINT fk_field_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
