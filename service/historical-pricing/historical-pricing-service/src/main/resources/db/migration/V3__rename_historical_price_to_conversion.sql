ALTER TABLE historical_price RENAME TO conversion;

DROP INDEX historical_price_idx;

CREATE UNIQUE INDEX conversion_idx
    ON conversion (source_unit, source_type, target_currency, date);
