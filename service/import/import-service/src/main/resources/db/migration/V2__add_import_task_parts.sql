ALTER TABLE import_task
    DROP COLUMN "reason";
ALTER TABLE import_task
    DROP COLUMN "status";
CREATE TABLE import_task_part
(
    id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    task_id UUID         NOT NULL REFERENCES import_task (id) ON DELETE CASCADE,
    name    VARCHAR(255) NOT NULL,
    status  VARCHAR(50)  NOT NULL,
    reason  VARCHAR(255)
);

CREATE UNIQUE INDEX import_task_part_task_id_name_idx ON import_task_part (task_id, name);
