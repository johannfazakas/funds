CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE "account"
(
    id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID        NOT NULL,
    name    VARCHAR(50) NOT NULL
);

CREATE INDEX account_user_id_idx ON "account" (user_id);
CREATE UNIQUE INDEX account_user_id_name_idx ON "account" (user_id, name);
