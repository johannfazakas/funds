CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE analytics_record
(
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID         NOT NULL,
    transaction_id   UUID         NOT NULL,
    date_time        TIMESTAMP    NOT NULL,
    account_id       UUID         NOT NULL,
    fund_id          UUID         NOT NULL,
    amount           DECIMAL      NOT NULL,
    unit             JSONB        NOT NULL,
    transaction_type VARCHAR(50)  NOT NULL,
    labels           JSONB        NOT NULL DEFAULT '[]'
);

CREATE INDEX analytics_record_user_id_idx ON analytics_record (user_id);
CREATE INDEX analytics_record_transaction_id_idx ON analytics_record (transaction_id);
CREATE INDEX analytics_record_account_id_idx ON analytics_record (account_id);
CREATE INDEX analytics_record_fund_id_idx ON analytics_record (fund_id);
CREATE INDEX analytics_record_date_time_idx ON analytics_record (date_time);
CREATE INDEX analytics_record_unit_idx ON analytics_record USING GIN (unit);
