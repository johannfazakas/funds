## Why

The performance report's investment stream uses aggregated UnitAmounts (summed by unit across all transactions), which causes two problems:

1. **Broken grouping by FINANCIAL_UNIT**: currency and instrument sides of the same OPEN_POSITION transaction are split into separate groups. The "VT" group sees instrument value but no investment; the "EUR" group sees investment but no instrument value. Neither group can compute meaningful profit.

2. **Incorrect cross-currency profit**: when the target currency differs from the investment currency (e.g., invested EUR, reporting in USD), the invested amount is converted at evaluation time rather than at each transaction's date. This conflates FX fluctuation with investment profit.

Both problems stem from aggregating away per-transaction information before computing metrics.

## What Changes

- Fetch raw `AnalyticsRecord` lists for the investment stream instead of aggregated `UnitAmounts`
- Pair currency and instrument records by `transactionId` to form investment positions
- When grouping by `FINANCIAL_UNIT`, group investment positions by the paired instrument's unit (not the currency's unit)
- Convert invested currency amounts at each transaction's date (historical cost) instead of at the evaluation date
- `totalInstrumentValue` continues to convert at the evaluation date (current market value)
- `totalProfit = totalInstrumentValue - totalInvestment` now reflects historical-cost investment vs current market value
- **BREAKING**: cross-currency `totalInvestment` and `totalProfit` values will change for ungrouped reports (historical cost vs present value). Grouped-by-FINANCIAL_UNIT reports will produce correct per-instrument profit where before they were split/broken.
- CLOSE_POSITION handling is out of scope; add TODOs where relevant for future cost-basis attribution

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ungrouped-performance-report`: `totalInvestment` changes from present-value conversion (at evaluation date) to historical-cost conversion (at each transaction's date). This affects cross-currency scenarios only; same-currency results are unchanged.
- `grouped-performance-report`: when grouped by `FINANCIAL_UNIT`, investment amounts are attributed to the paired instrument's group via `transactionId` pairing, enabling correct per-instrument profit calculation.

## Impact

- `PerformanceService`: major rework of the investment stream — switches from aggregated `UnitAmounts` to raw records with per-transaction processing
- `AnalyticsRecordRepository`: no new queries needed (`getRecords`/`getRecordsBefore` already exist)
- `ConversionSdk`: more conversion calls per bucket (one per historical transaction date instead of one per aggregated unit)
- `PerformanceServiceTest`: tests need updating for new conversion semantics and grouped scenarios
- No API model changes (`PerformanceDataTO` fields stay the same)
- No database schema changes
