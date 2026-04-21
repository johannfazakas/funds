ALTER TABLE analytics_record ADD COLUMN category VARCHAR(50);
UPDATE analytics_record SET category = labels->>0;
ALTER TABLE analytics_record DROP COLUMN labels;
