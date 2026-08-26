# Tasks — sse-metric-streaming

## 1. API models

- [x] 1.1 Add stream event TOs to analytics-api: `MetricsStreamBucketsTO` (granularity, buckets), `MetricsStreamValueTO` (queryId, bucket, values by group key), `MetricsStreamErrorTO` (message)

## 2. Streaming endpoint

- [x] 2.1 Add the `ktor-server-sse` dependency, install the SSE plugin, and add `POST /funds-api/analytics/v1/metrics/stream` to `MetricsApiRouting`: receive and map the request (shared helper with the aggregate route), then open the SSE session on the POST route
- [x] 2.2 Emit the `buckets` event, then forward `resolveFlow` emissions as `value` events via `ServerSentEvent` (domain → TO mapping at the boundary), then `complete`
- [x] 2.3 Catch mid-stream resolution failures and emit a terminal `error` event; verify client disconnect cancels the resolution scope

## 3. Tests

- [x] 3.1 Streaming API test: event order (buckets first, one value per query per bucket, terminal complete), query-id correlation, per-query bucket ordering, values equal to the aggregate endpoint's report
- [x] 3.2 Failure tests: invalid request → HTTP 400 with no stream; mid-stream failure (missing conversion rate) → `error` event, no `complete`

## 4. Web client

- [x] 4.1 `analyticsApi.ts`: `streamMetricsReport(userId, request, handlers, signal)` — fetch POST to the stream endpoint, SSE frame parser over the response ReadableStream, typed `onBuckets`/`onValue`/`onComplete`/`onError` dispatch
- [x] 4.2 `AnalyticsPage`: build the report state incrementally from events (buckets on first event, merge values per query/bucket, zero-fill new group keys), reusing the existing chart assembly
- [x] 4.3 Generate lifecycle: abort in-flight stream via `AbortController` before starting a new one; spinner until the `buckets` event; on `error` clear the chart and show the request-level error

## 5. Docs and verification

- [x] 5.1 Update `SERIES.md` (streaming endpoint consumes `resolveFlow`; event protocol pointer)
- [x] 5.2 Full analytics suite green; rebuild and redeploy `ff_analytics` and the web client; smoke-test progressive chart rendering end to end
