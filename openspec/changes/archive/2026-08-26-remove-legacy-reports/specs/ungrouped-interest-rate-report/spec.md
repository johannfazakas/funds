# ungrouped-interest-rate-report (delta)

## REMOVED Requirements

### Requirement: Interest rate report endpoint
**Reason**: Legacy endpoint removed; superseded by the multi-metric endpoint (`metric-report-api`).
**Migration**: Request `TOTAL_INTEREST_RATE`, `CURRENT_INTEREST_RATE` via `POST /funds-api/analytics/v1/metrics`.

### Requirement: Interest rate report computes two metrics per bucket per group
**Reason**: Both values are exposed metrics in `metric-catalog`, resolved per bucket by `metric-resolution`.
**Migration**: Same values under the same names in the metrics report.

### Requirement: Ungrouped interest rate report uses UNGROUPED group key
**Reason**: Ungrouped handling is defined once in `metric-report-api` for all metrics.
**Migration**: None — same semantics on the metrics endpoint.

### Requirement: Time-weighted return calculation via bisection
**Reason**: The XIRR-via-bisection calculation semantics (bracket, precision, annualization) move to `metric-catalog` (metric calculation semantics requirement); the calculator implementation is unchanged.
**Migration**: None — behavior preserved by the series engine.

### Requirement: Interest rate report tracks currency positions only
**Reason**: Calculation semantics carried over into `metric-catalog`.
**Migration**: None — behavior preserved by the series engine.

### Requirement: Current interest rate uses previous bucket valuation
**Reason**: Carried over into `metric-catalog`; implemented as resolver-internal state per `metric-resolution`.
**Migration**: None — behavior preserved by the series engine.
