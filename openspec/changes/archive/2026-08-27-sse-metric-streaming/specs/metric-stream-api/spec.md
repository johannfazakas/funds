# metric-stream-api (delta)

## ADDED Requirements

### Requirement: Streaming metrics endpoint
The analytics service SHALL expose `POST /funds-api/analytics/v1/metrics/stream` accepting the same request body as the aggregate metrics endpoint (request-level interval and target currency, queries with client-supplied unique ids, per-query grouping and filter) and responding with `text/event-stream`. The stream SHALL begin with a single `buckets` event carrying the granularity and the resolved bucket boundaries, followed by one `value` event per query per bucket — each carrying the query's echoed id, the bucket timestamp, and the per-group scalar values — and SHALL end with exactly one terminal event: `complete` on success or `error` on failure.

#### Scenario: Progressive value delivery
- **WHEN** a client streams a request with two queries over a monthly interval
- **THEN** a `buckets` event arrives first, then one `value` event per query per bucket each naming its query id, then a `complete` event

#### Scenario: Per-query bucket ordering
- **WHEN** value events for one query are observed in arrival order
- **THEN** their bucket timestamps are strictly increasing and match the announced buckets, regardless of how events from different queries interleave

#### Scenario: Grouped values in events
- **WHEN** a streamed query specifies a grouping
- **THEN** each of its `value` events maps group keys to values, and group keys MAY first appear in any bucket's event

#### Scenario: Values consistent with the aggregate endpoint
- **WHEN** the same request is sent to the streaming and the aggregate endpoint against unchanged data
- **THEN** the streamed values assembled per query and bucket equal the aggregate report's series

### Requirement: Streaming validation and failure semantics
The endpoint SHALL reject invalid requests (empty queries, duplicate or blank query ids, invalid interval, unknown or internal metric names) with a plain HTTP 400 before any stream output. Once streaming has begun, any resolution failure SHALL cancel the whole resolution and emit a terminal `error` event naming the failure — no further `value` events follow and no partial success is signalled. Client disconnection SHALL cancel the server-side resolution.

#### Scenario: Invalid request rejected before streaming
- **WHEN** a client sends a request with duplicate query ids to the streaming endpoint
- **THEN** the service responds HTTP 400 with an error body and no event stream is started

#### Scenario: Mid-stream failure is terminal
- **WHEN** a resolution node fails after some value events were already delivered
- **THEN** the stream emits an `error` event and closes, with no `complete` event and no further values for any query

#### Scenario: Client abort cancels resolution
- **WHEN** the client aborts the request mid-stream
- **THEN** the server cancels the resolution coroutine scope and releases the stream
