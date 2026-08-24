# Ungrouped Performance Report

## Purpose

Defines the performance report endpoint, metrics, and ungrouped behavior for tracking investment performance across time buckets.

## Requirements

### Requirement: Performance report endpoint
The analytics service SHALL expose a `POST /funds-api/analytics/v1/reports/performance` endpoint accepting an `AnalyticsReportRequestTO` with optional `groupBy` parameter and returning an `AnalyticsReportTO<PerformanceDataTO>`.

### Requirement: Performance report computes six metrics per bucket per group
Each group entry in a performance report SHALL contain a `PerformanceDataTO` with:
- `totalInvestment`: cumulative invested amount converted to target currency
- `currentInvestment`: investment amount within the current bucket
- `totalProfit`: cumulative profit (totalInstrumentValue - totalInvestment)
- `currentProfit`: profit change within the current bucket
- `totalInstrumentValue`: total market value of instruments converted to target currency
- `currencyValue`: total currency holdings converted to target currency

#### Scenario: Performance report with investments in two instruments
- **WHEN** a performance report is requested and the user has invested 1000 EUR in BTC and 500 EUR in ETH
- **THEN** totalInvestment SHALL reflect the sum of both investments converted to target currency, and totalInstrumentValue SHALL reflect the current market value of both BTC and ETH holdings

#### Scenario: Performance report tracks profit across buckets
- **WHEN** a monthly performance report is requested and totalProfit is 100 in January and the portfolio gains 50 in February
- **THEN** totalProfit SHALL be 150 in February and currentProfit SHALL be 50 in February

### Requirement: Ungrouped performance report uses UNGROUPED group key
When `groupBy` is null or not specified, the performance report SHALL return a single group entry per bucket with `groupKey: "UNGROUPED"`.

#### Scenario: Performance report without grouping
- **WHEN** a performance report is requested without `groupBy`
- **THEN** each time bucket SHALL contain exactly one group entry with `groupKey: "UNGROUPED"` and aggregated performance metrics across all transactions

### Requirement: Performance report separates investment from instrument transactions
The performance report SHALL use OPEN_POSITION transactions for investment amounts and OPEN_POSITION + CLOSE_POSITION transactions for instrument amounts, ensuring that buy/sell operations are correctly reflected in both investment and instrument value metrics.

#### Scenario: Closed position affects instrument value but not investment
- **WHEN** the user closes a position (CLOSE_POSITION transaction)
- **THEN** totalInstrumentValue SHALL decrease by the closed amount but totalInvestment SHALL remain unchanged

### Requirement: Performance report converts amounts to target currency
Investment amounts (totalInvestment, currentInvestment) SHALL be converted to the target currency using the conversion rate at each transaction's date (historical cost), not the bucket evaluation date. Instrument amounts (totalInstrumentValue) SHALL continue to use the conversion rate at the bucket evaluation date (current market value). Currency amounts (currencyValue) SHALL continue to use the conversion rate at the bucket evaluation date.

#### Scenario: Multi-currency portfolio with EUR target
- **WHEN** a performance report is requested with targetCurrency EUR and the user has USD and GBP investments
- **THEN** totalInvestment SHALL be the sum of each investment converted to EUR at its transaction date, and totalInstrumentValue SHALL be converted to EUR at the bucket evaluation date

#### Scenario: Same-currency investment
- **WHEN** a performance report is requested with targetCurrency EUR and all investments were made in EUR
- **THEN** totalInvestment SHALL equal the sum of EUR invested (historical-cost and present-value are identical for same-currency)

#### Scenario: Cross-currency investment with changing exchange rates
- **WHEN** the user invested 100 USD in January (rate: 1 USD = 0.90 EUR) and 100 USD in February (rate: 1 USD = 0.95 EUR) with targetCurrency EUR
- **THEN** totalInvestment in February SHALL be 185 EUR (90 + 95), not 190 EUR (200 × current rate of 0.95)

### Requirement: Performance report pairs investment records by transaction
The performance report SHALL pair currency and instrument records from the same OPEN_POSITION transaction using transactionId. Each paired investment position SHALL associate the currency amount spent with the instrument units acquired, enabling correct attribution of investment cost to specific instruments.

#### Scenario: Investment pairing via transactionId
- **WHEN** an OPEN_POSITION transaction has a currency record (EUR -1000) and an instrument record (VT +10) sharing the same transactionId
- **THEN** the performance report SHALL treat this as a single investment position: 1000 EUR invested in 10 VT
