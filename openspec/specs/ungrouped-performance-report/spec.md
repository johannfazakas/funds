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
All performance metrics SHALL be converted to the target currency specified in the request using conversion rates at each bucket's date.

#### Scenario: Multi-currency portfolio with EUR target
- **WHEN** a performance report is requested with targetCurrency EUR and the user has USD and GBP investments
- **THEN** all six metrics SHALL be expressed in EUR using conversion rates at the relevant bucket date
