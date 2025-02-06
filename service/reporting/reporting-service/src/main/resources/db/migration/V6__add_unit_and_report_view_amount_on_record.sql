ALTER TABLE report_record
    ADD COLUMN unit VARCHAR(50) NOT NULL DEFAULT 'unknown';
ALTER TABLE report_record
    ADD COLUMN unit_type VARCHAR(50) NOT NULL DEFAULT 'unknown';
ALTER TABLE report_record
    ADD COLUMN report_currency_amount NUMERIC(20, 8) NOT NULL DEFAULT 0;
