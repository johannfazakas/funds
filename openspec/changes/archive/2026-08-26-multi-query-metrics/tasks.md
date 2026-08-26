# Tasks — multi-query-metrics

## 1. API models

- [x] 1.1 Reshape `MetricsReportRequestTO`: request-level `interval` + `targetCurrency`, `queries: List<MetricQueryTO>` (client-supplied id, metric, optional grouping, optional filter); init-block validation (non-empty queries, unique query ids, from < to)
- [x] 1.2 Reshape `MetricsReportTO`: series entries echo their query id alongside metric, unit, currency, groups
- [x] 1.3 Update `MetricsReportRequestTOTest` for the new validation rules

## 2. Engine: context-keyed resolution

- [x] 2.1 Introduce `QueryContext` (grouping + normalized filter) in the domain and reshape `MetricResolutionRequest` into shared fields + query list
- [x] 2.2 Add context sensitivity declaration to `SeriesDefinition` (default: all dimensions) and declare filter-only sensitivity on `OpenPositionRecordsSeriesDefinition` and `PairedPositionsSeriesDefinition`
- [x] 2.3 Key node flows by `(series, projected context)` in `MetricResolutionService`: propagate each query's context down its closure, project per definition, reuse `shareIn` on key collisions
- [x] 2.4 Pass the node's context to `createResolver` and adapt resolver call sites (grouping/filter reads move from request to context)
- [x] 2.5 Assemble the report and `resolveFlow` emissions per query (query-keyed collectors bounded by the bucket clock)
- [x] 2.6 Update `MetricsApiRouting` to map the new TOs

## 3. Tests

- [x] 3.1 Engine sharing-matrix tests: identical contexts shared across queries, different filters isolated, grouping-insensitive chain shared while grouping-sensitive nodes stay separate, normalized-filter collision, shared-node failure cancels the whole request
- [x] 3.2 Rework `MetricsApiTest` request shapes to queries; add a multi-query scenario (same metric, two contexts)
- [x] 3.3 Rework `MetricsRegressionTest` to the new request shape with unchanged golden values (single-query degenerate case)

## 4. Web client

- [x] 4.1 Query editor list under the chart: add/remove/duplicate/collapse rows with metric, grouping, funds, units controls, each owning a generated query id (React key, persistence key, request correlation id); page starts with one default query
- [x] 4.2 Single multi-series chart component (replacing `ValueChart`/`GroupedValueChart`): dual Y-axes by unit type, one hue per query with shade variants per group, line identity = query label + group name
- [x] 4.3 Visibility controls: per-query eye toggle and per-line legend toggle (client-side only)
- [x] 4.4 Per-query group-name resolution (fund/account UUID → name by that query's grouping)
- [x] 4.5 Persist query set + shared controls in localStorage and restore on load
- [x] 4.6 Single request on Generate mapping all queries; request-level error shown, nothing plotted on failure

## 5. Docs and verification

- [x] 5.1 Update `SERIES.md` (context-keyed node identity, sensitivity projection)
- [x] 5.2 Full analytics suite green; rebuild and redeploy `ff_analytics` and the web client; smoke-test a multi-query chart end to end
