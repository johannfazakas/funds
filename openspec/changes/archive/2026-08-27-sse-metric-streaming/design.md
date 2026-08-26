# Design — sse-metric-streaming

## Context

`MetricResolutionService.resolveFlow(request): Flow<QueryBucketValue>` already emits `(queryId, bucket, values)` progressively on the shared bucket clock, with all-or-nothing cancellation. The aggregate endpoint collects this flow into a report; the streaming endpoint forwards it frame by frame instead. The client-generated query id (introduced with multi-query) is the correlation key that lets the browser route each frame to the right chart line without ordering assumptions.

## Goals / Non-Goals

**Goals:**
- Stream per-bucket values to the browser as the engine resolves them; chart fills progressively.
- Zero engine changes — the endpoint is a thin adapter over `resolveFlow`.
- Preserve all-or-nothing semantics: a mid-stream failure invalidates everything already delivered.
- Keep the aggregate endpoint untouched for the SDK and tests.

**Non-Goals:**
- Resume/reconnect (`Last-Event-ID`): a dropped stream means re-Generate; each run recomputes from current data.
- Heartbeats/keep-alive frames: local deployment, bucket cadence is the liveness signal.
- Replacing the aggregate endpoint or changing its contract.

## Decisions

### 1. POST + SSE over fetch, not EventSource

The request carries a JSON body (queries, interval, currency), and browser `EventSource` only does GET without a body. So the endpoint is a regular `POST /funds-api/analytics/v1/metrics/stream` responding `text/event-stream`, and the client consumes it with `fetch` + a small SSE frame parser over `response.body` (ReadableStream). This keeps one request shape for both endpoints and needs no query-string encoding of the request. `AbortController` cancels the HTTP request, which cancels the server coroutine scope and with it the whole resolution graph.

### 2. Event protocol

```
event: buckets   data: {"granularity":"MONTHLY","buckets":["2024-01-01T00:00", ...]}
event: value     data: {"queryId":"q-...","bucket":"2024-01-01T00:00","values":{"UNGROUPED":"123.45"}}
event: complete  data: {}
event: error     data: {"message":"..."}
```

- `buckets` is always first: the client allocates its x-axis and per-query slots before any value arrives.
- One `value` event per query per bucket; `values` maps group key (`apiValue`, e.g. fund UUID or `UNGROUPED`) to the scalar. Per query, buckets arrive in order; across queries, frames interleave arbitrarily — the id + bucket timestamp fully address each frame, so the client never depends on interleaving.
- `complete` and `error` are terminal; exactly one of them ends every started stream. After `error` the client discards all received frames (all-or-nothing at the UI level mirrors the engine's cancellation).
- Group keys appearing mid-interval are simply new keys in later `values` maps; the client zero-fills earlier buckets, matching the aggregate report's zero-backfill.

### 3. Server implementation: Ktor SSE plugin

The service installs the `ktor-server-sse` plugin (same Ktor 3.1.3 line) and the route uses an SSE session on the POST route (`route(..., HttpMethod.Post) { sse { ... } }` — the path+handler `sse(path)` convenience binds GET, so the session builder is nested under an explicit POST route), sending `ServerSentEvent(event = <name>, data = <json>)` per frame; the plugin owns framing and flushing. Request receiving and TO init-block validation run before the session starts, so invalid requests still surface as plain StatusPages 400s — this pre-stream/mid-stream boundary is pinned by the failure tests. Mid-stream, exceptions from collecting `resolveFlow` are caught in the session, emitted as the `error` event, and the session closes.

### 4. Event TOs in analytics-api

`MetricsStreamBucketsTO(granularity, buckets)`, `MetricsStreamValueTO(queryId: String, bucket: LocalDateTime, values: Map<String, BigDecimal>)`, `MetricsStreamErrorTO(message)` — serialized with the existing kotlinx setup (BigDecimal as string). The web layer maps `QueryBucketValue` (domain `QueryId`, `GroupKey`) to the TO at the boundary, same as the aggregate route.

### 5. Web client: incremental chart state

- `analyticsApi.ts` gains `streamMetricsReport(userId, request, handlers, signal)`: fetches the stream endpoint, parses SSE frames (split on double newline, accumulate partial chunks), dispatches typed handler callbacks (`onBuckets`, `onValue`, `onComplete`, `onError`).
- `AnalyticsPage` keeps a `streamedReport` state shaped like today's `MetricsReport` but built incrementally: `buckets` set on the first event, each `value` event merged into its query's series (group arrays extended and zero-filled as new keys appear). The existing chart assembly is reused unchanged — it re-renders from the same shape on every merge.
- Generate: abort any in-flight controller, clear state, start the stream; spinner until the `buckets` event; on `error`, clear the chart and show the request-level error banner (nothing partial stays visible).
- Per-frame `setState` at daily granularity over years is hundreds of updates; merges are batched per animation frame only if it proves janky — start with direct state merges (React 18 batches within microtasks), measure, don't pre-optimize.

## Risks / Trade-offs

- **Hand-rolled SSE parsing on the client** (fetch + frame parser, since `EventSource` cannot POST). The server side uses the plugin's standard framing; the streaming API test pins the event protocol so drift is caught. If the plugin's POST-route session wiring or its StatusPages interplay misbehaves on Ktor 3.1.3, the fallback is writing frames via `respondTextWriter` — the event protocol and tests are unchanged either way.
- **Proxy buffering** could delay frames (nginx serving the web client proxies nothing — the browser calls the analytics service directly, so no `X-Accel-Buffering` concerns today). If a proxy is ever introduced, buffering must be disabled for this route.
- **Chart churn** on large streams (see decision 5) — mitigated by measuring first; the frame protocol doesn't constrain the fix.
- **Two endpoints over one flow**: aggregate and stream can theoretically drift in mapping logic; both routes share the TO-mapping helpers in the web layer to keep the surface single-sourced.
