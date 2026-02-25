ALTER TABLE import_file ADD COLUMN import_configuration_id UUID REFERENCES import_configuration(id) ON DELETE SET NULL;

CREATE INDEX import_file_configuration_id_idx ON import_file (import_configuration_id);
