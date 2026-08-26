# SSE Metric Streaming

## Why

The metrics endpoint computes the whole report before answering: the engine already resolves bucket by bucket (`resolveFlow` emits one value per query per bucket as it becomes available), but the HTTP layer collects everything into one response and the chart stays empty until the slowest query's last bucket. For long intervals with conversion-heavy metrics this is a multi-second blank screen. The multi-query change deliberately put a client-generated query id on every series as the correlation seam for streaming — this change cashes that in: stream each bucket value to the browser as it is resolved and let the chart fill in progressively.

## What Changes

- **New SSE endpoint** `POST /funds-api/analytics/v1/metrics/stream`: same request body as the aggregate endpoint, responds with `text/event-stream`. Events: one `buckets` event first (granularity + bucket boundaries — the clock the client allocates its chart against), then one `value` event per query per bucket (`{queryId, bucket, values: {groupKey: value}}`) as the engine emits them, then a terminal `complete` event. Validation failures before the stream starts are plain HTTP 400s; a resolution failure mid-stream emits a terminal `error` event and closes — all-or-nothing is preserved (the client discards everything already plotted).
- **Aggregate endpoint unchanged**: `POST /metrics` stays as-is for the SDK, tests, and any non-streaming consumer. The streaming endpoint reuses `resolveFlow` directly — no engine changes.
- **Web client goes progressive**: the analytics page consumes the stream via `fetch` + SSE parsing (browser `EventSource` cannot POST a body), building the chart incrementally — points appear bucket by bucket, group lines appear as their group keys first show up. On `error` the chart is cleared and the request error shown; Generate aborts any in-flight stream before starting a new one.

Out of scope: resume/reconnection (`Last-Event-ID`) — every Generate is a fresh computation; heartbeats — bucket cadence is the liveness signal; caching.

## Capabilities

### New Capabilities

- `metric-stream-api`: the SSE endpoint — request contract, event protocol (buckets/value/complete/error), ordering guarantees, validation and failure semantics.

### Modified Capabilities

None. `metric-resolution` already specifies progressive bucket emission and per-query flows; the engine is consumed, not changed. `metric-report-api` (the aggregate endpoint) keeps its contract.

## Impact

- **analytics-api**: new event TOs (`MetricsStreamBucketsTO`, `MetricsStreamValueTO`, error payload) next to the existing report TOs; request TO reused as-is.
- **analytics-service**: `ktor-server-sse` plugin installed; new SSE route in `web/` forwarding `resolveFlow` emissions as server-sent events; `MetricsApiRouting` gains the `/stream` sibling route. No domain or engine changes.
- **Tests**: streaming API test — event order (buckets first, per-query value counts, terminal complete), per-query correlation ids, mid-stream failure emitting `error`, invalid request rejected with 400 before streaming.
- **client/web-client**: `analyticsApi.ts` gains a streaming client (fetch + SSE frame parser with `AbortController`); `AnalyticsPage` applies events incrementally to the chart state; loading indicator until the first event.
- **No impact**: analytics-sdk, other services, engine internals, aggregate endpoint consumers.
