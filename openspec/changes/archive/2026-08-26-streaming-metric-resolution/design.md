# Design — Streaming Metric Resolution

## Context

`unified-metric-resolution` established the metric DAG: a validated registry, a resolution engine with once-per-request memoization, and fifteen resolvers (four SQL leaves, `PAIRED_POSITIONS`, ten external metrics). Resolution is pull-based: a resolver is a pure function over its dependencies' *completed* whole-series outputs, so a parent cannot start until every dependency has finished all buckets, and cross-bucket needs are met by random access into full series.

Brainstorming established that no metric actually needs full-series dependency access: with a push model, every cross-bucket need (running balance, accumulated positions, previous valuation/profit) is naturally private accumulator state inside the node. The eventual goal is progressive delivery of buckets to the UI; this change builds the streaming engine and seam without adding the streaming endpoint.

## Goals / Non-Goals

**Goals:**
- Buckets resolve progressively through the DAG; independent branches and pipelined buckets run concurrently.
- Same-bucket-only dependency access; a uniform resolver contract that hides statefulness and dependency arity.
- Identical HTTP behavior and bit-identical numbers — existing parity/integration suites pass unchanged.
- A `resolveFlow` seam a future SSE endpoint can consume.

**Non-Goals:**
- The streaming/SSE endpoint itself and any client changes.
- Per-bucket leaf SQL (leaves keep one query per request and emit progressively from memory).
- Backpressure sophistication (full replay buffering is deliberate at this scale).
- Cross-request caching, registry/catalog/API changes.
- Conversion-rate prefetching at leaves and within-node bucket parallelism (prepare/combine split) — explored and deliberately deferred; buckets stay strictly sequential within a node, with cross-node pipelining as the only concurrency.

## Decisions

### 1. Emission model: Previous seed + bucket clock

Every node produces a `Flow<MetricEmission>`: first `Previous(value)` carrying pre-interval state (may be empty), then exactly one `Bucket(dateTime, value)` per interval bucket, in order. The interval's bucket sequence is the shared clock; the engine — not resolvers — owns the one-slice-per-bucket invariant, which is what makes zipping dependency streams safe and deterministic. Slice payloads replace the whole-series `MetricOutput` containers:

| Node kind | Previous payload | Bucket payload |
|---|---|---|
| Record leaf | records before interval | records in bucket |
| Amount leaves | `GroupedUnitAmounts` before interval | `GroupedUnitAmounts` in bucket |
| `PAIRED_POSITIONS` | previous positions | bucket positions |
| External metrics | empty | `Map<GroupKey, BigDecimal>` |

### 2. Uniform per-bucket resolver contract

```kotlin
interface MetricBucketResolver {
    suspend fun resolvePrevious(previous: DependencySlices): Slice
    suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): Slice
}
```

`MetricDefinition` carries a `resolverFactory: (MetricResolutionRequest) -> MetricBucketResolver`. Dependencies arrive as aligned same-bucket slices keyed by name, regardless of count (0 for leaves, 2+ for `TOTAL_PROFIT`). Statefulness is invisible to the engine: stateless metrics (`NET_CHANGE`, `CURRENT_INVESTMENT`) ignore state; stateful ones (`BALANCE`, values, investments, profits, interest rates) keep private fields seeded in `resolvePrevious`. The per-request factory confines state to one resolution, and single-coroutine-per-node execution makes it thread-safe without synchronization.

*Alternative considered:* named combinators (`mapBuckets`/`scanBuckets`/`zipBuckets`) encoding shape in the API. Rejected: statefulness and arity are resolver internals; one contract, engine owns all plumbing.

### 2b. Typed domain metrics

Metric identity is a sealed domain hierarchy `Metric<T : MetricSlice>` (data objects; external metrics extend `Metric.External` which fixes `T = Scalars` and carries the `MetricTO` mapping; internal metrics extend `Metric.Internal<T>`). `MetricDefinition<T>` binds a metric to a `MetricBucketResolver<T>` factory, so a resolver cannot produce the wrong slice type, and `DependencySlices` is keyed by `Metric<T>` with a single typed `get` — requesting a dependency under the wrong slice type is a compile-time error. This deletes the string `MetricNames`, `MetricOutputType`, the per-type slice accessors, and the runtime exposed-output validation (all unrepresentable now); the remaining startup validation is catalog completeness (every `Metric.entries` has exactly one definition), dependency registration, and acyclicity.

*Alternative considered:* a plain domain enum mirroring `MetricTO` plus internal names. Rejected: it fixes name typos but leaves slice-type access as a runtime cast — the sealed hierarchy delivers both for marginally more declaration code.

### 2d. Definition classes with nested resolvers

`MetricDefinition<T>` is an abstract class (`metric`, `dependencies`, abstract `createResolver(request)`); each metric is a concrete subclass with its resolver as a private nested `inner` class. The shared lexical scope ties definition and resolver together: each dependency is named once as a companion constant (e.g. `AMOUNTS = Metric.TransactionAmounts`) referenced by both the `dependencies` list and the resolver's slice accesses, so declared-vs-accessed drift is structurally avoided without helpers or extractor parameters. App-scoped collaborators (conversions, repository) are constructor state on the definition and reach the resolver through the outer scope; per-request state lives only on the resolver instance. Shared computations become abstract intermediate definitions (`UnitAmountsLeafMetricDefinition` for the three amount leaves, `CumulativeValueMetricDefinition` for the two value metrics).

*Alternatives considered along the way:* free extractor lambdas passed to resolver constructors (wiring adjacent but consistency unchecked), arity-overloaded `metricDefinition` helpers (compile-enforced naming-once, but multiple constructors and factory lambdas taking extractor arguments), and arity-typed resolver interfaces (per-arity interfaces plus erased engine adapters). The nested-class shape achieves naming-once and definition-level containment with a single definition base class and plain `createResolver(request)` factory method.

### 2c. Request TO shape

`MetricsReportRequestTO` nests `interval: ReportIntervalTO` (granularity, from, to — `init`-validated `from < to`) and `filter: ReportFilterTO` (fundIds, units), and names the grouping criteria `grouping`, mirroring the domain `MetricResolutionRequest`. Validation stays at the DTO level (init blocks → 400 via StatusPages).

### 3. Planner and concurrency

Per request, inside one `coroutineScope`:

1. Expand and topo-sort the dependency closure.
2. For each node, build its flow: fresh resolver from the factory, driven over a zip of its dependencies' flows (`resolvePrevious` on the seeds, then `resolveBucket` per tick).
3. Share each node's flow with `shareIn(scope, Lazily, replay = buckets + 1)` — resolve-once under fan-out, and full replay makes diamond topologies deadlock-free (backpressure intentionally traded away; ≤ hundreds of buckets makes buffering free).
4. Collect the requested metrics' flows, bounded by the bucket clock (`take(buckets)` per metric) — shared flows never signal completion, so consumers terminate by count, not by stream end.

One coroutine per node; buckets strictly sequential within a node (safe accumulator state), concurrency across nodes (independent branches overlap their conversion calls; pipeline skew lets leaves produce bucket N+1 while parents compute N). First failure cancels the scope and propagates to StatusPages exactly as today. `Lazily` keeps unrequested subgraphs dormant.

### 4. Assembly seam

`MetricResolutionService` gains internal `resolveFlow(request)` emitting per-bucket events; the public `resolve(request)` collects it into the existing `MetricResolutionReport`. Group keys first appearing mid-interval are zero-backfilled at assembly, keeping responses byte-identical to today. A future SSE endpoint is simply a second consumer of `resolveFlow` (its clients must then tolerate late-appearing groups — deferred).

### 5. Direct rewrite, parity as the arbiter

All resolvers are rewritten to `MetricBucketResolver` in place — no adapter wrapping old whole-series resolvers, since the codebase is small and `MetricsParityTest`/`MetricsApiTest` pin behavior. Per-node arithmetic order is unchanged, so values stay bit-identical; `TOTAL_INVESTMENT` switches from re-converting the full position list each bucket to an incremental running sum (same fold order, same values, less work).

## Risks / Trade-offs

- [Zip misalignment if a node emits fewer/more slices than the clock] → invariant lives only in the engine's node-driving loop; resolvers cannot emit directly.
- [Unbounded replay buffers] → bounded in practice by interval length; acceptable trade for deadlock-free diamonds and simple fan-out.
- [Subtle nondeterminism from concurrency] → per-node sequential arithmetic preserves determinism; parity suite verifies bit-identical output; engine test asserts resolve-once under concurrent consumers.
- [Coroutine/flow bugs harder to debug than sequential code] → engine kept small; resolvers stay synchronous-looking suspend functions; progressive-emission and cancellation covered by dedicated tests.
- [Ordering with previous change] → `unified-metric-resolution` must be archived (or its specs synced) first so the `metric-resolution` capability exists in main specs when this change is archived.

## Migration Plan

Internal-only refactor: no API, schema, or deploy-order implications. Ship analytics-service; parity/integration suites gate the change. Rollback = revert the service.

## Open Questions

None.
