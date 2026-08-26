# Multi-Query Metrics

## Why

The analytics UI supports exactly one query at a time: one metric, one grouping, one filter. Comparing anything — balance by fund against total profit for a single fund — means generating reports one after another. The metrics endpoint has the same limitation baked in: `metrics[]` share a single `grouping` and `filter` per request. A Grafana-style model — one chart, many queries, each with its own metric, grouping, and filters — is the natural shape for exploration, and the series engine's resolve-once machinery can be generalized to share computation across queries in a single request.

## What Changes

- **API reshape (breaking, UI updated in the same change)**: the request's `metrics[]` + shared `grouping`/`filter` are replaced by `queries[]`, each carrying a client-supplied `id` (opaque, unique within the request), `metric`, optional `grouping`, optional `filter`. `interval` and `targetCurrency` stay request-level and apply to every query. The response is keyed by the echoed query id, not by metric — a metric may appear in several queries, and identical queries stay distinct series. The id is the correlation seam for the future SSE endpoint (streamed segments name their query) and doubles as the UI's editor-row and persistence key.
- **Engine: context-keyed nodes with projected sharing**: the resolution graph's node identity generalizes from `Series` to `(Series, query context)` where context = (grouping, normalized filter). Each query's context propagates down its dependency closure; each `SeriesDefinition` declares which context dimensions affect its output, and the node key uses the *projected* context — so grouping-insensitive nodes (raw records, paired positions) are shared across queries that differ only in grouping. Same-key nodes resolve once via the existing shared-flow machinery.
- **Failure semantics unchanged**: all-or-nothing — any node failure cancels the whole graph and the request errors; no partial charts.
- **Web client**: Grafana-style query editors under the chart (add/remove/collapse, per-query visibility toggle, legend click per line), shared controls (granularity, interval, currency) on top, a single multi-series chart with dual Y-axes (currency left, percentage right), one hue per query with shade variants per group line, query set persisted in localStorage.

Out of scope: per-query intervals (all queries share the bucket clock), SSE/progressive delivery (the query id in the response is the seam for it), caching.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `metric-report-api`: endpoint request/response reshaped to per-query grouping/filter with query-keyed series; validation extended to per-query rules.
- `metric-resolution`: node identity and once-per-request sharing become context-keyed with per-definition context projection; uniform-parameter requirement narrowed to interval/granularity/currency.

## Impact

- **analytics-api**: `MetricsReportRequestTO` reshaped (`queries[]` of `MetricQueryTO`), `MetricsReportTO` series keyed by query; validation moves to the new TOs' init blocks.
- **analytics-service**: `MetricResolutionRequest` carries query list; planner keys node flows by `(series, projected context)`; `SeriesDefinition` declares context sensitivity; resolvers receive their query's context; report assembly and `resolveFlow` emit per query. `SERIES.md` updated.
- **Tests**: engine tests for cross-query sharing/isolation; `MetricsApiTest`/`MetricsRegressionTest` reshaped to the new request format (expected values unchanged — single-query requests are the degenerate case).
- **client/web-client**: `AnalyticsPage` reworked around a query list; `ValueChart`/`GroupedValueChart` consolidated into one multi-series chart component.
- **No impact**: metric catalog and registry capabilities, ingestion, other services.
