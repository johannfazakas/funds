ALTER TABLE "account"
    ADD COLUMN unit VARCHAR(50) NOT NULL DEFAULT 'unknown';
ALTER TABLE "account"
    ADD COLUMN unit_type VARCHAR(50) NOT NULL DEFAULT 'unknown';
ALTER TABLE "account"
    DROP COLUMN type;
ALTER TABLE "account"
    DROP COLUMN currency;
ALTER TABLE "account"
    DROP COLUMN symbol;
