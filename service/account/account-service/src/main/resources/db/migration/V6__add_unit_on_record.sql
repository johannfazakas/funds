ALTER TABLE record
    ADD COLUMN unit VARCHAR(50) NOT NULL DEFAULT 'unknown';
ALTER TABLE record
    ADD COLUMN unit_type VARCHAR(50) NOT NULL DEFAULT 'unknown';