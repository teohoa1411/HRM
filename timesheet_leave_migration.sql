-- =============================================================
-- Migration: Timesheet lock + Leave half-day period
-- Date: 2026-04-13
-- =============================================================

-- 1. Bảng khóa timesheet theo tháng (cấp công ty)
CREATE TABLE IF NOT EXISTS timesheet_period_locks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id  BIGINT       NOT NULL,
    year_month  VARCHAR(7)   NOT NULL COMMENT 'Format: YYYY-MM',
    locked      BOOLEAN      NOT NULL DEFAULT FALSE,
    locked_by   BIGINT       NULL     COMMENT 'userId của người khóa',
    locked_at   DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_company_month (company_id, year_month),
    INDEX idx_company_month_locked (company_id, year_month, locked)
);

-- 2. Thêm cột locked_by_leave vào timesheets
ALTER TABLE timesheets
    ADD COLUMN IF NOT EXISTS locked_by_leave BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'true = entry tạo tự động bởi leave đã duyệt, nhân sự không được sửa';

-- 3. Thêm cột half_day_period vào leave_requests
ALTER TABLE leave_requests
    ADD COLUMN IF NOT EXISTS half_day_period ENUM('MORNING', 'AFTERNOON') NULL
        COMMENT 'Chỉ có giá trị khi is_half_day = true';

-- Verify
SHOW COLUMNS FROM timesheet_period_locks;
SHOW COLUMNS FROM timesheets LIKE 'locked_by_leave';
SHOW COLUMNS FROM leave_requests LIKE 'half_day_period';
