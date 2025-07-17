ALTER TABLE transaction
    ADD COLUMN external_id VARCHAR(100) NOT NULL DEFAULT uuid_generate_v4()::VARCHAR;

CREATE UNIQUE INDEX transaction_external_id_user_id_idx ON transaction (external_id, user_id);
