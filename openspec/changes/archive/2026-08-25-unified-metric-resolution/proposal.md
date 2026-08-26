# Unified Metric Resolution

## Why

The analytics UI splits "Report" and "Metric" into two dropdowns, but the metric selection is client-side only: the browser fetches a full performance or interest-rate report and extracts one field. On the backend, `PerformanceService` and `InterestRateService` are duplicated pipelines that independently re-query records, re-pair positions, and re-convert currencies, even though their metrics share the same underlying data. Adding a metric today means either extending a monolithic report or cloning a third pipeline.

Restructuring analytics resolution around a dependency DAG of metrics gives a single UI dimension ("metric"), computes shared intermediate data once per request, and makes new metrics cheap to add.

## What Changes

- Introduce a single **metric** concept in analytics-service: each metric has a name, an output type, dependencies on other metrics, and a resolver function. Exposed metrics produce bucketed scalars with a unit type (`CURRENCY` or `PERCENTAGE`); internal metrics produce richer intermediate outputs (record sets, dated cash flows, instrument holdings) and are not addressable via the API.
- Add a **metric registry** validated at startup (dependencies exist, graph is acyclic), exposed via `GET /funds-api/analytics/v1/metrics` so the UI dropdown is server-driven.
- Add a **metric resolution engine** that expands the dependency closure of requested metrics, resolves it in topological order, and resolves each node exactly once per request (within-request dedup; no cross-request caching in this iteration). Resolver functions receive the full bucketed series of each dependency (e.g. `CURRENT_INTEREST_RATE` needs the previous bucket's valuation).
- Add `POST /funds-api/analytics/v1/metrics` accepting a list of metrics plus granularity, interval, filters, `groupBy`, and `targetCurrency`, returning one series per requested metric with its unit.
- Cover all ten exposed metrics, replacing the functional scope of all four existing report endpoints: `BALANCE`, `NET_CHANGE`, `TOTAL_INVESTMENT`, `CURRENT_INVESTMENT`, `TOTAL_INSTRUMENT_VALUE`, `CURRENCY_VALUE`, `TOTAL_PROFIT`, `CURRENT_PROFIT`, `TOTAL_INTEREST_RATE`, `CURRENT_INTEREST_RATE`.
- Strangler rollout: the new `metrics` package and endpoints land alongside the existing report endpoints, with parity integration tests asserting equal results per bucket and group against the existing implementations. The web client switches to the new API (single metric dropdown, no client-side extraction). Existing endpoints are untouched here and removed in a later cleanup change.
- The existing bisection `InterestRateCalculator` is reused as an internal detail of the interest-rate resolvers.

## Capabilities

### New Capabilities

- `metric-registry`: Metric definitions (name, output type, unit type, dependencies, exposed/internal), startup validation of the dependency graph, and the metric discovery endpoint.
- `metric-resolution`: The resolution engine — dependency closure expansion, topological execution, once-per-request resolution, full-series dependency access, uniform propagation of request parameters (interval, granularity, filters, groupBy, target currency).
- `metric-report-api`: The multi-metric report endpoint — request model, response model (per-metric series with unit), and error handling (unknown or internal metric requests).
- `metric-catalog`: The concrete metric graph — the ten exposed metrics, the internal metrics they depend on, their unit types, and computation semantics equivalent to the existing report specs (parity contract).

### Modified Capabilities

<!-- none — existing report endpoints and their specs are unchanged in this iteration; their removal is a follow-up change -->

## Impact

- **analytics-service**: new `metrics` package (registry, engine, resolvers, routing); no changes to existing `PerformanceService`, `InterestRateService`, balance/net-change services or their routes.
- **analytics-api / analytics-sdk**: new request/response TOs and SDK methods for the metric endpoints.
- **web-client**: `AnalyticsPage.tsx` replaces the Report + Metric dropdowns with a single metric dropdown populated from `GET /metrics`; `extractMetric` client-side projection is removed; `analyticsApi.ts` calls the new endpoint.
- **Tests**: parity integration tests (TestContainers) comparing new engine output against the four existing endpoints on identical seeded data.
- **Follow-up change (out of scope)**: removal of the four legacy report endpoints and their specs once the UI migration is verified.
