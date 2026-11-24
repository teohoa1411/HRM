-- V51: Add sell_price_unit to billing_records (separate from cost price_unit)
--      Add price_unit to statement_line_items (for correct HOURLY amount calculation)

ALTER TABLE billing_records
    ADD COLUMN sell_price_unit VARCHAR(10) NOT NULL DEFAULT 'MONTHLY'
        COMMENT 'Price unit used for sell amount calculation: HOURLY|DAILY|MONTHLY';

ALTER TABLE statement_line_items
    ADD COLUMN price_unit VARCHAR(10) NOT NULL DEFAULT 'MONTHLY'
        COMMENT 'Billing unit for this statement line: HOURLY|DAILY|MONTHLY';
