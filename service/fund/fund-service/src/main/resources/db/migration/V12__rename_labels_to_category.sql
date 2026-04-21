ALTER TABLE label RENAME TO category;

ALTER TABLE record ADD COLUMN category VARCHAR(50);
UPDATE record SET category = labels->>0;
DROP INDEX record_labels_gin;
ALTER TABLE record DROP COLUMN labels;
CREATE INDEX record_category_idx ON record (category);
