# Streaming Metric Resolution

## Why

The metric engine introduced by `unified-metric-resolution` resolves each metric's entire bucketed series before any dependent metric can start: an upstream metric waits for its dependencies to complete all buckets. This serializes work that is naturally concurrent (independent branches, per-bucket conversion calls, XIRR computation) and structurally blocks the end goal of progressively streaming buckets to the UI as they resolve. The full-series dependency access it relies on is also broader than any metric actually needs.

## What Changes

- Replace whole-series resolution with **progressive, bucket-by-bucket streaming** through the metric DAG: every node emits one `Previous` seed (pre-interval state) followed by exactly one bucket slice per interval bucket, in order — the bucket-clock invariant, owned by the engine.
- **BREAKING (internal architecture only)**: `MetricOutput`'s whole-series containers dissolve into per-bucket slice payloads (`List<AnalyticsRecord>`, `GroupedUnitAmounts`, `List<InvestmentPosition>`, `Map<GroupKey, BigDecimal>`); the resolver contract changes from whole-series functions to a uniform per-bucket `MetricBucketResolver` (`resolvePrevious` + `resolveBucket`), instantiated per request via a factory so accumulator state is request-confined. All ~15 resolvers are rewritten directly (no legacy adapter).
- Dependency access narrows to **same-bucket only**, regardless of dependency count; all cross-bucket needs become private resolver state (running balances, accumulated positions, previous valuation).
- The engine becomes a planner: topo-sort the closure, wire each node as its resolver over a zip of its dependencies' flows, share each node's flow with full replay (resolve-once fan-out, deadlock-free diamonds), one coroutine per node inside one `coroutineScope` per request (first failure cancels the graph).
- The service exposes an internal `resolveFlow(request)`; today's `resolve()` becomes its collector, folding emissions into the existing `MetricResolutionReport` with group zero-backfill at assembly.
- Introduce a **typed domain `Metric<T>` hierarchy** (sealed objects carrying the slice type as a type parameter, external metrics carrying their `MetricTO` mapping) replacing string metric names throughout the registry, definitions, and dependency access — wrong-slice-type access becomes a compile-time error; startup validation additionally requires every domain metric to have exactly one definition.
- **BREAKING (API request shape)**: `MetricsReportRequestTO` groups `granularity`/`from`/`to` into a nested `interval` (validated `from < to`), `fundIds`/`units` into a nested `filter`, and renames `groupBy` to `grouping`; the web client is updated accordingly.
- **Unchanged**: endpoint paths, response TOs, SDK method surface, metric catalog, and all computed numbers — the existing parity and integration suites remain the arbiter. An SSE/streaming endpoint is explicitly out of scope; this change only creates the seam for it.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `metric-resolution`: full-series dependency access is replaced by bucket-aligned, same-bucket-only access; progressive emission, the uniform per-bucket resolver contract, and concurrent streaming execution become requirements. (Prerequisite: archive or sync `unified-metric-resolution` first so this capability exists in main specs.)
- `metric-registry`: definitions become a typed, closed domain hierarchy (compile-time slice types, external status via API mapping); startup validation extends to catalog completeness.
- `metric-report-api`: request shape regrouped (`interval`, `filter`) and `groupBy` renamed to `grouping`.

## Impact

- **analytics-service**: `service/metrics` engine (`MetricResolutionService`, `MetricDefinition`) and all resolver files rewritten; `domain` metric types reshaped (`MetricEmission`/slices replace `MetricOutput` series containers; `MetricResolutionContext` replaced by per-bucket inputs); `METRICS.md` updated.
- **Tests**: resolver/engine unit tests reworked to the new contract; new engine tests for resolve-once fan-out, observable progressive emission, and cancellation. Parity and API integration tests unchanged and must stay green.
- **No changes** to analytics-api, analytics-sdk, web client, legacy report services, or infrastructure.
