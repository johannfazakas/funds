CREATE TABLE transaction_property
(
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id        UUID        NOT NULL,
    transaction_id UUID        NOT NULL REFERENCES transaction (id),
    key            VARCHAR(50) NOT NULL,
    value          VARCHAR(50) NOT NULL,
    type           VARCHAR(50) NOT NULL
);

-- TODO(Johann) Expenses by fund - do I need indexes by foreign keys?
CREATE INDEX transaction_property_user_id_key_value_index ON transaction_property (user_id, key, value);

CREATE TABLE record_property
(
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id        UUID        NOT NULL,
    transaction_id UUID        NOT NULL REFERENCES transaction (id),
    record_id      UUID        NOT NULL REFERENCES record (id),
    key            VARCHAR(50) NOT NULL,
    value          VARCHAR(50) NOT NULL
);

CREATE INDEX record_property_user_id_key_value_index ON record_property (user_id, key, value);

ALTER TABLE transaction
    DROP COLUMN metadata;

ALTER TABLE record
    DROP COLUMN metadata;
