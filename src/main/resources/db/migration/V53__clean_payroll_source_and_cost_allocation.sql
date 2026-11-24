-- Fresh development schema only: no backfill is required.
ALTER TABLE internal_statement_entries ADD COLUMN expected_hours DECIMAL(4,1) NOT NULL DEFAULT 0;
ALTER TABLE internal_statement_summaries ADD COLUMN total_expected_hours DECIMAL(7,1) NOT NULL DEFAULT 0;
ALTER TABLE internal_statement_summaries ADD COLUMN revision BIGINT NOT NULL DEFAULT 1;
ALTER TABLE payroll_records ADD COLUMN source_statement_revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE payroll_cost_allocations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT NOT NULL,
  payroll_record_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  payroll_month VARCHAR(7) NOT NULL,
  customer_id BIGINT NULL,
  allocation_ratio DECIMAL(9,6) NOT NULL,
  allocated_amount DECIMAL(18,2) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  UNIQUE KEY uk_payroll_cost_allocation (payroll_record_id, customer_id),
  INDEX idx_payroll_cost_allocation_month (company_id, payroll_month)
);
