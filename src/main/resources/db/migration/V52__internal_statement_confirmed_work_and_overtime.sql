-- Confirmed internal work is the payroll source.  OT is deliberately separate
-- from regular hours so it never inflates worked days.
ALTER TABLE internal_statement_entries
    ADD COLUMN raw_ot_hours DECIMAL(4,1) NOT NULL DEFAULT 0,
    ADD COLUMN adjusted_ot_hours DECIMAL(4,1) NOT NULL DEFAULT 0,
    ADD COLUMN ot_multiplier DECIMAL(4,2) NOT NULL DEFAULT 1.50,
    ADD COLUMN ot_adjustment_reason VARCHAR(500) NULL;

ALTER TABLE internal_statement_summaries
    ADD COLUMN total_raw_ot_hours DECIMAL(7,1) NOT NULL DEFAULT 0,
    ADD COLUMN total_adjusted_ot_hours DECIMAL(7,1) NOT NULL DEFAULT 0;

ALTER TABLE payroll_records
    ADD COLUMN overtime_amount DECIMAL(18,2) NOT NULL DEFAULT 0;
