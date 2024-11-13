CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE "import_task"
(
    id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID        NOT NULL,
    status  VARCHAR(50) NOT NULL,
    reason  VARCHAR(255)
);

CREATE INDEX import_task_user_id_idx ON "import_task" (user_id);
