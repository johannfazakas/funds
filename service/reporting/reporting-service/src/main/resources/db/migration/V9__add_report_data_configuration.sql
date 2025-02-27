ALTER TABLE report_view
    DROP COLUMN currency;
ALTER TABLE report_view
    DROP COLUMN labels;
ALTER TABLE report_view
    ADD COLUMN data_configuration JSONB;