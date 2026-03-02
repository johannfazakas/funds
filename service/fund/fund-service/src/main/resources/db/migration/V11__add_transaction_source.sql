ALTER TABLE transaction ADD COLUMN source VARCHAR(200);
CREATE INDEX transaction_source_user_id_idx ON transaction (source, user_id);
