ALTER TABLE record ADD COLUMN labels_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb;
UPDATE record SET labels_jsonb = CASE
    WHEN labels = '' THEN '[]'::jsonb
    ELSE to_jsonb(string_to_array(labels, ','))
END;
ALTER TABLE record DROP COLUMN labels;
ALTER TABLE record RENAME COLUMN labels_jsonb TO labels;
