CREATE TABLE IF NOT EXISTS test_timesheet_monthly (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id   BIGINT UNSIGNED NOT NULL,
    user_id      BIGINT UNSIGNED NOT NULL,
    year_month   VARCHAR(7)      NOT NULL COMMENT 'YYYY-MM',
    description  TEXT            NULL     COMMENT 'Monthly notes/summary',
    PRIMARY KEY (id),
    UNIQUE KEY uk_monthly (user_id, year_month)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

DROP TABLE IF EXISTS test_timesheet_monthly;
SELECT 'OK' AS result;
