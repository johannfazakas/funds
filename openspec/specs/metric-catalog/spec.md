# Metric Catalog

## Purpose

Defines the concrete set of metrics in the registry: the ten exposed metrics covering the legacy report endpoints, the internal intermediate metrics they share, and the parity guarantees with legacy report semantics.

## Requirements

### Requirement: Exposed metric set
The registry SHALL expose exactly these ten metrics, covering the functional scope of the four legacy report endpoints:

| Metric | Unit type |
|---|---|
| `BALANCE` | CURRENCY |
| `NET_CHANGE` | CURRENCY |
| `TOTAL_INVESTMENT` | CURRENCY |
| `CURRENT_INVESTMENT` | CURRENCY |
| `TOTAL_INSTRUMENT_VALUE` | CURRENCY |
| `CURRENCY_VALUE` | CURRENCY |
| `TOTAL_PROFIT` | CURRENCY |
| `CURRENT_PROFIT` | CURRENCY |
| `TOTAL_INTEREST_RATE` | PERCENTAGE |
| `CURRENT_INTEREST_RATE` | PERCENTAGE |

#### Scenario: Catalog completeness
- **WHEN** the discovery endpoint is called
- **THEN** exactly these ten metrics are returned with the unit types above

### Requirement: Internal intermediate metrics
The catalog SHALL include internal metrics for shared intermediate data: record-set leaves that are the only metrics reading the repository, each issuing a query scoped to the transaction types its consumers need (position records for investment metrics; transaction records for balance/net-change metrics), plus paired positions (dated cash flows paired by transactionId, performed once per request) and instrument holdings. Exposed metrics SHALL obtain records, positions, and holdings only through these internal metrics, never by querying the repository directly. Internal wiring MAY be refined during implementation as long as exposed-metric semantics are preserved.

#### Scenario: Positions paired once
- **WHEN** both performance metrics and interest-rate metrics are resolved in one request
- **THEN** transactionId pairing of open-position records is performed once, by the paired-positions metric

### Requirement: Parity with legacy report semantics
Each exposed metric SHALL produce values equal to the corresponding field of the legacy analytics report endpoints on identical data, intervals, filters, and grouping: `BALANCE` and `NET_CHANGE` per the balance and net-change reports; `TOTAL_INVESTMENT`, `CURRENT_INVESTMENT`, `TOTAL_INSTRUMENT_VALUE`, `CURRENCY_VALUE`, `TOTAL_PROFIT`, `CURRENT_PROFIT` per the performance report specs (`ungrouped-performance-report`, `grouped-performance-report`), including historical-cost conversion of investment at transaction date and bucket-date conversion of values; `TOTAL_INTEREST_RATE`, `CURRENT_INTEREST_RATE` per the interest-rate report specs (`ungrouped-interest-rate-report`, `grouped-interest-rate-report`), including bisection precision and the previous-bucket valuation treatment of `CURRENT_INTEREST_RATE`.

#### Scenario: Ungrouped parity
- **WHEN** the metric endpoint and a legacy report endpoint are called with identical seeded data, interval, granularity, filters, and target currency
- **THEN** every metric value equals the corresponding legacy report field in every bucket

#### Scenario: Grouped parity
- **WHEN** both APIs are called with `groupBy` set to each supported grouping criteria over multi-group seeded data
- **THEN** every per-group metric value equals the corresponding legacy grouped report value in every bucket, including per-group `CURRENT_INTEREST_RATE` using that group's previous-bucket valuation

#### Scenario: Multi-currency historical-cost parity
- **WHEN** seeded data contains investments in multiple currencies at dates with differing conversion rates
- **THEN** `TOTAL_INVESTMENT` matches the legacy performance report's historical-cost conversion in every bucket
