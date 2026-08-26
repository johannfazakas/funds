# grouped-interest-rate-report (delta)

## REMOVED Requirements

### Requirement: Grouped interest rate report by fund
**Reason**: Legacy endpoint removed; grouping for all metrics is defined once in `metric-report-api`.
**Migration**: Request the interest-rate metrics with `grouping: FUND` on the metrics endpoint.

### Requirement: Grouped interest rate report by account
**Reason**: Superseded by `metric-report-api` grouping.
**Migration**: `grouping: ACCOUNT` on the metrics endpoint.

### Requirement: Grouped interest rate report by financial unit
**Reason**: Superseded by `metric-report-api` grouping.
**Migration**: `grouping: FINANCIAL_UNIT` on the metrics endpoint.

### Requirement: Grouped interest rate report by category
**Reason**: Superseded by `metric-report-api` grouping.
**Migration**: `grouping: CATEGORY` on the metrics endpoint.

### Requirement: Per-group position accumulation for interest rate calculation
**Reason**: Per-group accumulation is resolver-internal state in the series engine.
**Migration**: None — behavior preserved by the series engine.

### Requirement: Per-group valuation for current interest rate
**Reason**: Carried over into `metric-catalog`; implemented as resolver-internal state per `metric-resolution`.
**Migration**: None — behavior preserved by the series engine.
