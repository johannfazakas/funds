ALTER TABLE report_record
    ADD COLUMN record_id UUID NOT NULL DEFAULT uuid_generate_v4();
