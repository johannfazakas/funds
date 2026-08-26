# ungrouped-performance-report (delta)

## REMOVED Requirements

### Requirement: Performance report endpoint
**Reason**: Legacy endpoint removed; superseded by the multi-metric endpoint (`metric-report-api`).
**Migration**: Request `TOTAL_INVESTMENT`, `CURRENT_INVESTMENT`, `TOTAL_INSTRUMENT_VALUE`, `CURRENCY_VALUE`, `TOTAL_PROFIT`, `CURRENT_PROFIT` via `POST /funds-api/analytics/v1/metrics`.

### Requirement: Performance report computes six metrics per bucket per group
**Reason**: The six values are exposed metrics in `metric-catalog`, resolved per bucket by `metric-resolution`.
**Migration**: Same values under the same names in the metrics report.

### Requirement: Ungrouped performance report uses UNGROUPED group key
**Reason**: Ungrouped handling is defined once in `metric-report-api` for all metrics.
**Migration**: None — same response shape semantics on the metrics endpoint.

### Requirement: Performance report separates investment from instrument transactions
**Reason**: Calculation semantics carried over into `metric-catalog` (metric calculation semantics requirement).
**Migration**: None — behavior preserved by the series engine.

### Requirement: Performance report converts amounts to target currency
**Reason**: Conversion semantics (historical-cost at transaction date for investment, bucket-date for values) carried over into `metric-catalog`.
**Migration**: None — behavior preserved by the series engine.

### Requirement: Performance report pairs investment records by transaction
**Reason**: Pairing by `transactionId` is the `PAIRED_POSITIONS` internal series, covered by `metric-catalog`/`metric-resolution`.
**Migration**: None — behavior preserved by the series engine.
