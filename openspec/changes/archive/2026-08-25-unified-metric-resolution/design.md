# Design — Unified Metric Resolution

## Context

analytics-service currently exposes four report endpoints (`POST /reports/balance|net-change|performance|interest-rate`). `PerformanceService` and `InterestRateService` each re-implement record fetching, transactionId-based position pairing, and currency conversion over the same `analytics_record` table. The web client renders a "Report" dropdown plus a client-side "Metric" dropdown that projects one field out of an already-fetched full report (`extractMetric` in `analyticsApi.ts`).

The existing report semantics are specified in `analytics-report-model`, `ungrouped-performance-report`, `ungrouped-interest-rate-report`, `grouped-performance-report`, and `grouped-interest-rate-report`. Those specs remain the source of truth for computation semantics; this change introduces a new resolution architecture that must reproduce them exactly (parity).

## Goals / Non-Goals

**Goals:**
- One user-facing concept: a metric, requestable individually or in batches through a single endpoint.
- Shared intermediate data (records, paired positions, holdings, valuations) resolved once per request via a dependency DAG.
- Server-driven metric discovery so clients stop hardcoding metric lists.
- Numeric parity with the four existing report endpoints, proven by integration tests.
- Land alongside existing code (strangler); no behavior change to existing endpoints.

**Non-Goals:**
- Cross-request caching or Kafka-driven invalidation (explicitly deferred).
- Removing the legacy report endpoints or the legacy reporting-service (follow-up changes).
- Replacing the bisection XIRR algorithm (kept as-is; swappable behind a resolver later).
- Per-metric `groupBy` (groupBy is request-level and uniform).
- Forecast buckets (a legacy reporting-service concept; not part of analytics-service).

## Decisions

### 1. Single "metric" abstraction with typed outputs (no separate "layer" concept)

Every node in the graph is a metric. A `MetricDefinition` carries: name, output type, unit type (exposed metrics only), dependencies (metric names), and a resolver function. Output types form a sealed hierarchy:

- `BucketedScalars` — `bucket × group → BigDecimal`; the only output shape exposable via the API.
- `RecordSet` — opening records + per-bucket records for a specific repository query (transaction types + request filters).
- `CashFlows` — dated cash flows (paired open/close positions) per bucket/group.
- `Holdings` — instrument units held per bucket/group.

Metrics whose output is not `BucketedScalars` are internal: not listed by the discovery endpoint, rejected with 400 if requested.

*Why not scalar-only metrics with dependencies?* XIRR cannot be derived from per-bucket scalar aggregates — it needs individual dated cash flows; aggregation destroys the timing information. *Why not two concepts (metric vs layer)?* Same machinery either way; one graph and one registry is simpler, with output type and exposure as properties.

### 2. Metric DAG

```
 EXPOSED  %         totalInterestRate        currentInterestRate
                        │        │              │         │ (uses bucket N−1
                        │        └──────┬───────┘         │  valuation as
                        │               │                 │  synthetic flow)
 EXPOSED  currency      │    totalInstrumentValue ────────┘
                        │        │      │     │
                        │        │      │     ├────▶ totalProfit   = value − totalInvestment
                        │        │      │     └────▶ currentProfit = value − currentInvestment
                        │        │      │                │              │
 INTERNAL CashFlows  pairedPositions    │      totalInvestment   currentInvestment
                        │               │             │                │
 INTERNAL Holdings      │        instrumentHoldings   │                │
                        │               │             │                │
 EXPOSED  currency   currencyValue   balance     netChange             │
                        │               │             │                │
                        └───────┬───────┘             │                │
                                │                     │                │
 INTERNAL RecordSet      positionRecords       transactionRecords
                         (OPEN/CLOSE_POSITION  (transaction types used
                          query)                by balance / netChange)
                                └──────────┬──────────┘
                          AnalyticsRecordRepository (type-specific queries)
                            Postgres analytics_record
```

- Leaf `RecordSet` metrics are the only nodes that touch the repository, each issuing a **type-specific query** (see Decision 3); metrics that need the same record set share one node and therefore one query.
- `PAIRED_POSITIONS` performs the transactionId pairing once; both performance and interest-rate metrics consume it.
- Currency conversion is **not** a graph node: it is keyed by `(unit, date)` rather than by bucket, and `ConversionSdk` already caches rates (Caffeine, 24h). Resolvers call `ConversionRateService` directly.
- The exact dependency edges may be refined during implementation as long as exposed-metric semantics keep parity; the catalog spec pins the exposed metrics and their semantics, not the internal wiring.

### 3. Type-specific leaf queries, not a single union fetch

Leaf `RecordSet` metrics issue precise repository queries scoped to the transaction types their consumers need (e.g. `positionRecords` fetches only OPEN_POSITION/CLOSE_POSITION records; `transactionRecords` fetches the types balance/netChange aggregate). A request may therefore hit the database more than once — one query per distinct leaf in the closure — but never loads rows no metric will use, and keeps filtering in SQL where the existing `toDbFilter(transactionTypes)` mechanism already lives.

*Alternative considered:* a single `recordStore` leaf fetching the union of all types, with in-memory filtering per metric. Rejected: it moves type filtering out of the database, loads irrelevant rows for narrow requests, and couples every metric to one query's shape. Per-node once-per-request dedup still guarantees that metrics sharing a leaf share its query.

### 4. Resolution engine

`MetricRegistry` is built at startup from the static list of definitions and validated: all dependencies exist, graph is acyclic (fail fast on boot). Per request, the engine:

1. Expands the dependency closure of the requested metrics.
2. Orders it topologically.
3. Resolves each node once into a request-scoped output map.
4. Returns a `MetricResolutionReport` (buckets + one scalar series per requested metric), so callers don't recompute bucket boundaries.

Resolution domain types (`MetricOutput`, `MetricResolutionRequest/Context/Report`, position pairing and grouping helpers) live in the `domain` package; the registry, definitions, resolvers, and engine live under `service/metrics` alongside a `METRICS.md` documenting the graph.

Resolver functions receive the request parameters plus their dependencies' **full bucketed series** (not a per-bucket slice). This is required by `CURRENT_INTEREST_RATE`, which uses the previous bucket's valuation as a synthetic opening position, and costs nothing for same-bucket compositions.

### 5. API surface

```
POST /funds-api/analytics/v1/metrics
  { metrics: ["TOTAL_PROFIT", "TOTAL_INTEREST_RATE"],
    granularity, from, to, fundIds?, units?, groupBy?, targetCurrency }
→ { buckets: [...bucket boundaries...],
    series: [ { metric, unit: "CURRENCY" | "PERCENTAGE", currency?, values | groups } ] }

GET /funds-api/analytics/v1/metrics
→ [ { metric, unit } ]   (exposed metrics only)
```

Metric identifiers are a serializable `MetricTO` enum (UNIX_CASE) carrying the unit type; internal metrics are not enum members, so they cannot be requested at all. Request validation lives at the DTO level: `MetricsReportRequestTO` `require`s a non-empty metric list and a valid interval in its `init` block, and unknown metric names fail enum deserialization — both surface as 400 via the StatusPages `BadRequestException` handler with the root cause message. Request parameters mirror the existing `AnalyticsReportRequestTO` (granularity, interval, filters, groupBy, targetCurrency) and apply uniformly to every requested metric. New TOs live in analytics-api; SDK methods in analytics-sdk.

### 6. Strangler rollout with parity tests

New code lands in a `metrics` package in analytics-service; existing services and routes are untouched. Parity integration tests (TestContainers, seeded `analytics_record` data) call each legacy endpoint and the new metric endpoint on identical inputs and assert equal values per bucket and per group, for all ten metrics, grouped and ungrouped. Divergence blocks the UI switch. The web client migrates only after parity is green. Legacy endpoint removal is a separate cleanup change.

### 7. UI: single server-driven metric dropdown, client-side labels

`AnalyticsPage.tsx` replaces the Report + Metric dropdowns with one metric dropdown populated from `GET /metrics`. `extractMetric` and the hardcoded option lists are deleted. Unit type drives value formatting (currency vs percentage). Display labels live in a small client-side map keyed by metric name — the discovery endpoint returns only `metric` and `unit`, keeping presentation out of the backend; a metric missing from the map falls back to its raw name. Single-metric selection in this iteration; the response shape already supports multi-metric batches for later.

## Risks / Trade-offs

- [Grouped `CURRENT_INTEREST_RATE` parity — per-group previous-bucket valuation is the subtlest legacy behavior] → Dedicated parity cases with multi-group seeded data; treat the grouped interest-rate specs as the arbiter.
- [Historical-cost FX conversion of `TOTAL_INVESTMENT` (transaction-date vs bucket-date rates) is easy to get subtly wrong in a shared pipeline] → `PAIRED_POSITIONS` preserves per-flow dates; parity tests cover multi-currency seeds.
- [Full-series dependency outputs held in memory per request] → Bounded by request interval and personal-finance data volumes; acceptable without caching.
- [Divergence found between legacy implementations themselves (latent bugs)] → Surface and decide explicitly (fix legacy or document intended behavior) rather than silently matching one side.
- [Two parallel report APIs live simultaneously] → Time-boxed by the follow-up cleanup change; UI switches wholesale, not per-report.

## Legacy Divergences Found During Implementation

Parity holds on all well-defined inputs (verified by `MetricsParityTest` across ungrouped + all four grouping criteria). Three legacy behaviors were found where exact replication was wrong or impossible; the new engine deliberately diverges:

1. **Legacy interest-rate crashes on in-interval positions.** `InterestRateService` includes interval records with `dateTime >= bucketStart` in every bucket — for any OPEN_POSITION after the first bucket's start date this both double-counts the position in later buckets and violates the calculator's `positions.all { date <= valuationDate }` precondition (HTTP 500). Legacy is only well-defined when in-interval positions fall exactly on the interval start. The new engine uses the coherent rule: a position is included from the first bucket whose start date is ≥ the position date. Both implementations agree on all inputs where legacy doesn't crash. **Confirmed on real imported data**: the legacy `/reports/interest-rate` endpoint returns 500 ("Positions after valuation date provided.") for a 2025 monthly interval, while the new `TOTAL_INTEREST_RATE`/`CURRENT_INTEREST_RATE` metrics resolve correctly; balance and performance metrics match legacy exactly on the same data, ungrouped and grouped.
2. **Legacy does not currency-convert pre-interval interest positions.** Opening positions use raw amounts while in-interval positions are converted at record date. The new engine converts all positions at their record date. Identical when positions are denominated in the target currency.
3. **Percentage independence from target currency is precision-bounded.** The bisection terminates on valuation distance (±0.001), so rates computed under different target currencies match only within calculator precision (~4 decimals), not bit-exactly.

Additionally, per-metric group sets are minimal: the new engine omits groups its metric has no data for, where the legacy combined reports zero-filled them (parity treats an absent group as zero).

## Migration Plan

1. Ship analytics-service with new endpoints alongside legacy ones (no deploy-order constraints; new tables: none).
2. Verify parity in integration tests; run both APIs against real imported data locally as a sanity check.
3. Switch web-client to the new endpoint.
4. Follow-up change: remove legacy report endpoints, their services, and archive/update the four legacy report specs.

Rollback at any step = point the UI back at legacy endpoints; no data migration involved.

## Open Questions

None — display labels are client-side (Decision 7) and leaf metrics use type-specific queries (Decision 3).
