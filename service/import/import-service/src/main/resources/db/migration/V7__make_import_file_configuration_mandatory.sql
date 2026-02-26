DELETE FROM import_file WHERE import_configuration_id IS NULL;
ALTER TABLE import_file ALTER COLUMN import_configuration_id SET NOT NULL;
ALTER TABLE import_file DROP CONSTRAINT IF EXISTS import_file_import_configuration_id_fkey;
ALTER TABLE import_file ADD CONSTRAINT import_file_import_configuration_id_fkey
    FOREIGN KEY (import_configuration_id) REFERENCES import_configuration(id);
