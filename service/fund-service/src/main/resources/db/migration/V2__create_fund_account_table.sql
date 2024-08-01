CREATE TABLE fund_account
(
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID NOT NULL,
    fund_id    UUID NOT NULL REFERENCES fund (id),
    account_id UUID NOT NULL
);
