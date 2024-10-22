CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE "fund"
(
    id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID        NOT NULL,
    name    VARCHAR(50) NOT NULL
);

CREATE INDEX fund_user_id_idx ON "fund" (user_id);
