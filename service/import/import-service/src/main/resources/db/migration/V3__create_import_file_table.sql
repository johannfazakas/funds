CREATE TABLE import_file
(
    id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id   UUID         NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    type      VARCHAR(50)  NOT NULL,
    s3_key    VARCHAR(512) NOT NULL,
    status    VARCHAR(50)  NOT NULL
);

CREATE INDEX import_file_user_id_idx ON import_file (user_id);
