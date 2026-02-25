CREATE TABLE import_configuration
(
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID         NOT NULL,
    name       VARCHAR(255) NOT NULL,
    matchers   JSONB        NOT NULL DEFAULT '{}',
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX import_configuration_user_id_idx ON import_configuration (user_id);
