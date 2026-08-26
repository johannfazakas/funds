# grouped-performance-report (delta)

## REMOVED Requirements

### Requirement: Grouped performance report by fund
**Reason**: Legacy endpoint removed; grouping for all metrics is defined once in `metric-report-api`.
**Migration**: Request the performance metrics with `grouping: FUND` on the metrics endpoint.

### Requirement: Grouped performance report by account
**Reason**: Superseded by `metric-report-api` grouping.
**Migration**: `grouping: ACCOUNT` on the metrics endpoint.

### Requirement: Grouped performance report by financial unit
**Reason**: Superseded by `metric-report-api` grouping.
**Migration**: `grouping: FINANCIAL_UNIT` on the metrics endpoint.

### Requirement: Grouped performance report by category
**Reason**: Superseded by `metric-report-api` grouping.
**Migration**: `grouping: CATEGORY` on the metrics endpoint.

### Requirement: Grouped performance report accumulates state per group
**Reason**: Per-group accumulation is resolver-internal state in the series engine (`metric-resolution` bucket-aligned dependency access).
**Migration**: None — behavior preserved by the series engine.
