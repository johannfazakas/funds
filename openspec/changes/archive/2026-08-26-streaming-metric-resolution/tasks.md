# Tasks — Streaming Metric Resolution

## 1. Domain model

- [x] 1.1 Replace `MetricOutput` series containers with the emission model in `domain`: `MetricEmission` (`Previous`, `Bucket`) and the sealed per-bucket `Slice` payloads (records, unit amounts, positions, scalars); keep `InvestmentPosition` and grouping helpers
- [x] 1.2 Define the `MetricBucketResolver` contract and `DependencySlices` accessor (typed slice lookup by dependency name) in `domain`
- [x] 1.3 Rework `MetricDefinition` to carry a per-request `resolverFactory`; adjust `MetricRegistry` validation accordingly

## 2. Streaming engine

- [x] 2.1 Rewrite `MetricResolutionService` as the planner: topo-sort the closure, drive each node's resolver over zipped dependency flows (previous seed then one slice per bucket), share each node with lazy full-replay, one coroutine per node in a per-request `coroutineScope`
- [x] 2.2 Add internal `resolveFlow(request)` and implement `resolve(request)` as its collector folding emissions into `MetricResolutionReport`, with group zero-backfill at assembly
- [x] 2.3 Unit-test the engine: shared dependency resolved once under fan-out; parent emits bucket N before dependencies emit later buckets (observable progressiveness); empty buckets stay aligned; failure in one node cancels the graph and fails the request; state confined between concurrent requests

## 3. Resolver rewrite

- [x] 3.1 Rewrite the four leaf resolvers to query once and emit previous + per-bucket slices
- [x] 3.2 Rewrite `PAIRED_POSITIONS` to pair per-bucket records into per-bucket cash flows
- [x] 3.3 Rewrite `BALANCE` and `NET_CHANGE` as per-bucket resolvers (running-balance state; stateless)
- [x] 3.4 Rewrite `TOTAL_INVESTMENT` (incremental running converted sum) and `CURRENT_INVESTMENT`
- [x] 3.5 Rewrite `TOTAL_INSTRUMENT_VALUE` and `CURRENCY_VALUE` (running unit-amount state, convert at bucket date)
- [x] 3.6 Rewrite `TOTAL_PROFIT` (same-bucket join) and `CURRENT_PROFIT` (previous-profit state)
- [x] 3.7 Rewrite `TOTAL_INTEREST_RATE` (accumulated-position state) and `CURRENT_INTEREST_RATE` (previous-valuation state seeded from previous holdings)
- [x] 3.8 Adapt the resolver unit tests to the new contract, keeping the given-when-then names and expected values unchanged

## 4. Typed metrics and request shape (added during implementation)

- [x] 4.a Introduce the sealed `Metric<T : MetricSlice>` domain hierarchy (exposed metrics carrying `MetricTO`, internal metrics typed by slice), replacing `MetricNames` strings and `MetricOutputType`
- [x] 4.b Make `MetricDefinition`/`MetricBucketResolver` generic in the slice type and key `DependencySlices` by `Metric<T>` with a single typed `get`
- [x] 4.c Extend startup validation to catalog completeness (every domain metric has exactly one definition); drop the now-unrepresentable exposed-output validation
- [x] 4.d Regroup `MetricsReportRequestTO` into nested `interval`/`filter`, rename `groupBy` to `grouping` (TO + domain + web client); add `metric-registry` and `metric-report-api` deltas
- [x] 4.e Restructure metrics as concrete `MetricDefinition` subclasses with nested inner resolvers (dependency constants named once per class; abstract `UnitAmountsLeafMetricDefinition`/`CumulativeValueMetricDefinition` for shared logic; `createResolver(request)` factory method)

## 5. Verification and docs

- [x] 5.1 Run the untouched parity and API integration suites and confirm all values remain identical
- [x] 5.2 Update `METRICS.md` (components section: emission model, `MetricBucketResolver`, planner; graph unchanged)
- [x] 5.3 Run full builds and tests for the analytics modules
- [x] 5.4 Archive or sync `unified-metric-resolution` before archiving this change so the `metric-resolution` capability exists in main specs
