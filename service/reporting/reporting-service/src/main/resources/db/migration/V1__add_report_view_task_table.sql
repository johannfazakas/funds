CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE report_view_task
(
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id        UUID        NOT NULL,
    status         VARCHAR(50) NOT NULL,
    report_view_id UUID,
    reason         VARCHAR(255)
);

CREATE INDEX report_view_task_user_id_idx ON report_view_task (user_id);
