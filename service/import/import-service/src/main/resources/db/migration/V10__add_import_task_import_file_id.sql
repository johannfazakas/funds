ALTER TABLE import_task ADD COLUMN import_file_id UUID REFERENCES import_file(id);
