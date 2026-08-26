# analytics-report-model (delta)

## REMOVED Requirements

### Requirement: Generic analytics report model
**Reason**: The generic `AnalyticsReportTO<T>` envelope existed only for the legacy per-report endpoints, all removed in this change. The metrics endpoint has its own report shape (`MetricsReportTO`, covered by `metric-report-api`).
**Migration**: Consumers use `POST /funds-api/analytics/v1/metrics`.

### Requirement: CURRENCY renamed to FINANCIAL_UNIT in GroupingCriteria
**Reason**: Historical rename record; `GroupingCriteria` lives on as part of the metrics request and is covered by `metric-report-api`.
**Migration**: None — behavior unchanged.

### Requirement: Sealed GroupKey interface with per-criteria variants
**Reason**: Implementation detail of the analytics domain, retained in code and exercised through `metric-resolution`/`metric-report-api` grouping requirements; no longer tracked as a standalone capability.
**Migration**: None — code unchanged.

### Requirement: Split AnalyticsRecordFilter into input and database filters
**Reason**: Implementation detail of the analytics domain retained in code; the metrics request filter behavior is covered by `metric-report-api`.
**Migration**: None — code unchanged.

### Requirement: Transaction type filtering in repository
**Reason**: Implementation detail retained in code; leaf query scoping is covered by `metric-resolution` (leaves query once with scoped filters).
**Migration**: None — code unchanged.

### Requirement: Unified repository methods with optional groupBy
**Reason**: Implementation detail retained in code, serving the series leaves.
**Migration**: None — code unchanged.

### Requirement: Raw record queries in repository
**Reason**: Implementation detail retained in code, serving the open-position records leaf.
**Migration**: None — code unchanged.
