# Design — multi-query-metrics

## Context

The metrics endpoint resolves `metrics[]` under a single request-wide context (interval, filter, grouping, target currency). The series engine keys its resolution graph by `Series` alone — valid only because the context is ambient. The UI mirrors this: one metric dropdown, one grouping, one filter. Multi-query support requires per-query grouping/filter while keeping one server round trip and preserving the engine's resolve-once and bucket-clock properties.

## Goals / Non-Goals

**Goals:**
- One request computes N queries, each `(metric, grouping?, filter?)`, sharing interval/granularity/targetCurrency.
- Maximal safe cross-query computation sharing inside the engine.
- Grafana-style query UX: add as many queries as wanted, toggle queries and individual lines.
- All-or-nothing failure: any error cancels everything and surfaces as the request error.

**Non-Goals:**
- Per-query intervals or granularities (would break the shared bucket clock).
- Progressive/SSE delivery (deferred; the per-query response identity is its seam).
- Backward compatibility of the request TO (personal project; UI ships in the same change).

## Decisions

### 1. Request/response shape

```
POST /funds-api/analytics/v1/metrics
{ interval, targetCurrency, queries: [ { id, metric, grouping?, filter? }, ... ] }
```

Response: buckets + one series entry **per query**, in request order, carrying the query's echoed `id`, metric, unit, currency (for CURRENCY unit), and per-group values. Query-keyed (not metric-keyed) because the same metric may appear with different contexts — and two *identical* queries remain two series (the engine shares all their nodes internally but fans the emissions out per id). The `id` is client-generated and opaque to the server (Grafana `refId` style); it decouples response/stream routing from request ordering, so mid-flight editor reorders or deletions in the UI resolve by id lookup, and the future SSE endpoint tags each streamed segment with it without any contract change. Validation in TO init blocks: non-empty `queries`, ids unique within the request, `from < to`; unknown/internal metric names rejected per query naming the offender.

### 2. Query context as part of node identity

Domain gains `QueryContext(grouping: GroupingCriteria?, filter: AnalyticsInputRecordFilter)` with normalized equality (sorted id/unit sets, empty ≡ absent). The planner walks each query's dependency closure propagating the query's context unchanged — a dependency inherits its consumer's context. Node flows are stored in a map keyed by `SeriesNode(series, context)`; key collisions reuse the existing `shareIn` flow, so cross-query resolve-once needs no new flow machinery.

### 3. Context projection per definition (the flow simplification)

Each `SeriesDefinition` declares its context sensitivity (subset of {GROUPING, FILTER}); the node key uses the context *projected* onto that set. Grouping-insensitive nodes — `OPEN_POSITION_RECORDS`, `PAIRED_POSITIONS` — collapse across queries that differ only in grouping, so the records/positions chain feeding five metrics runs once per distinct filter. SQL-grouping leaves and all scalar metrics are sensitive to both dimensions. Projection is a per-definition declaration plus one key function in the planner; correctness falls back gracefully — an over-sensitive declaration only costs duplicate work, never wrong values. An under-sensitive declaration is the risk to guard in review: sensitivity is declared explicitly on every definition (no default), full sensitivity being the norm.

### 4. Resolver context

`createResolver(request)` becomes `createResolver(context)` where context bundles the shared request fields (userId, interval, targetCurrency) with the node's own `QueryContext` — resolvers already read `request.grouping`/`request.filter`, so call sites change mechanically. The bucket clock, `Previous` seeding, zip alignment, and per-node sequential execution are untouched: all nodes still run on the one interval.

### 5. All-or-nothing failure

Unchanged from today: the whole resolution runs in one `coroutineScope`; the first node failure cancels every node (shared or not) and the request returns the error. No per-query isolation, no partial responses — explicit user decision to avoid partial-chart ambiguity.

### 6. Web client

- Top bar: granularity, from/to, target currency, Generate (explicit — one click, one request).
- Under the chart: a list of collapsible query editors (metric, grouping, funds multiselect, units multiselect), add/remove/duplicate; page starts with one default query (ungrouped BALANCE) so it is never empty. Each editor owns a generated query id used as its React key, its localStorage identity, and the request/response correlation id.
- Chart: single multi-series component replacing `ValueChart`/`GroupedValueChart`; line identity = query label + resolved group name; dual Y-axes (CURRENCY left in target currency, PERCENTAGE right); one hue per query, shade variants per group line.
- Visibility: eye icon on a query row hides all its lines (client-side only); legend click toggles a single line. Hidden state does not affect the request.
- Group-name resolution (fund/account UUID → name) becomes per-query, driven by that query's grouping.
- The query set + shared controls persist in localStorage and are restored on load.

## Risks / Trade-offs

- **Under-declared context sensitivity** would silently share nodes whose outputs differ → wrong numbers. Mitigation: sensitivity is an explicit, mandatory declaration on every definition; the engine test suite includes a sharing matrix (same-context shared, different-filter isolated, grouping-insensitive chain shared) asserting both sharing and isolation.
- **Breaking API change**: the old request shape stops working. Accepted — single consumer (the web client) ships in the same change; `MetricsRegressionTest` values carry over since single-query requests are the degenerate case.
- **Chart legibility** with many grouped queries (line explosion). Mitigated by per-query visibility toggles and hue/shade coloring; no hard cap imposed.
