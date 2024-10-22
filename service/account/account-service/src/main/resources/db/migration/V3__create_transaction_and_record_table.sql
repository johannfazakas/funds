CREATE TABLE transaction
(
    id        UUID PRIMARY KEY     DEFAULT uuid_generate_v4(),
    user_id   UUID        NOT NULL,
    date_time TIMESTAMPTZ NOT NULL,
    metadata  JSONB       NOT NULL DEFAULT '{}'::JSONB
);

CREATE TABLE record
(
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id        UUID           NOT NULL,
    transaction_id UUID           NOT NULL REFERENCES transaction (id),
    account_id     UUID           NOT NULL REFERENCES "account" (id),
    amount         NUMERIC(20, 8) NOT NULL
);
