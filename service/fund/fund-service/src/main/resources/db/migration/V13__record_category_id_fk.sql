ALTER TABLE record ADD COLUMN category_id UUID;

UPDATE record r
SET category_id = c.id
FROM category c
WHERE r.category = c.name AND r.user_id = c.user_id;

ALTER TABLE record DROP COLUMN category;
DROP INDEX IF EXISTS record_category_idx;

ALTER TABLE record ADD CONSTRAINT fk_record_category FOREIGN KEY (category_id) REFERENCES category(id);
CREATE INDEX record_category_id_idx ON record (category_id);
