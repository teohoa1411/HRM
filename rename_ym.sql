ALTER TABLE timesheet_monthly RENAME COLUMN ym TO `year_month`;
ALTER TABLE timesheet_attachments RENAME COLUMN ym TO `year_month`;
SELECT 'Done' AS status;
