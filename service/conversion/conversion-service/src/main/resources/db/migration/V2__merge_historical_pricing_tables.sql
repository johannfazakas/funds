CREATE TABLE historical_price
(
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_unit     VARCHAR(50)    NOT NULL,
    source_type     VARCHAR(50)    NOT NULL,
    target_currency VARCHAR(50)    NOT NULL,
    date            DATE           NOT NULL,
    price           DECIMAL(20, 8) NOT NULL
);

CREATE UNIQUE INDEX historical_price_idx
    ON historical_price (source_unit, source_type, target_currency, date);

INSERT INTO historical_price (id, source_unit, source_type, target_currency, date, price)
SELECT id, source_currency, 'currency', target_currency, date, price
FROM currency_pair_historical_price;

INSERT INTO historical_price (id, source_unit, source_type, target_currency, date, price)
SELECT id, symbol, 'instrument', currency, date, price
FROM instrument_historical_price;

DROP TABLE currency_pair_historical_price;
DROP TABLE instrument_historical_price;
