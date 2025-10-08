CREATE TABLE transaction
(
    id          UUID PRIMARY KEY     DEFAULT uuid_generate_v4(),
    user_id     UUID         NOT NULL,
    external_id VARCHAR(100) NOT NULL DEFAULT uuid_generate_v4()::VARCHAR,
    type        VARCHAR(20)  NOT NULL DEFAULT 'SINGLE_RECORD',
    date_time   TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX transaction_external_id_user_id_idx ON transaction (external_id, user_id);