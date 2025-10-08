CREATE TABLE record
(
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id        UUID           NOT NULL,
    transaction_id UUID           NOT NULL REFERENCES transaction (id),
    account_id     UUID           NOT NULL REFERENCES "account" (id),
    fund_id        UUID           NOT NULL REFERENCES fund (id),
    amount         NUMERIC(20, 8) NOT NULL,
    unit_type      VARCHAR(50)    NOT NULL,
    unit           VARCHAR(50)    NOT NULL,
    labels         VARCHAR(100)   NOT NULL DEFAULT ''
);