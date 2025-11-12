CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE currency_pair_historical_price
(
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_currency VARCHAR(50)    NOT NULL,
    target_currency VARCHAR(50)    NOT NULL,
    date            DATE           NOT NULL,
    price           DECIMAL(20, 8) NOT NULL
);

CREATE UNIQUE INDEX currency_pair_historical_price_idx
    ON currency_pair_historical_price (source_currency, target_currency, date);

CREATE TABLE instrument_historical_price
(
    id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    symbol   VARCHAR(50)    NOT NULL,
    currency VARCHAR(50)    NOT NULL,
    date     DATE           NOT NULL,
    price    DECIMAL(20, 8) NOT NULL
);

CREATE UNIQUE INDEX instrument_historical_price_idx
    ON instrument_historical_price (symbol, currency, date);
