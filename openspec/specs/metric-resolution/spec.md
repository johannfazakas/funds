# Metric Resolution

## Purpose

Defines the engine that resolves requested metrics by expanding their dependency closure, resolving each dependency exactly once per request in topological order, and applying uniform request parameters across the whole closure.

## Requirements

### Requirement: Dependency closure resolution
Given a set of requested metrics, the resolution engine SHALL expand the full dependency closure and resolve it in topological order, so that every metric's dependencies are resolved before the metric itself. Only the requested metrics SHALL be returned to the caller.

#### Scenario: Transitive dependencies are resolved
- **WHEN** `TOTAL_INTEREST_RATE` is requested
- **THEN** the engine resolves its transitive dependencies (including paired positions and the position-records leaf) before resolving `TOTAL_INTEREST_RATE`, and the result contains only `TOTAL_INTEREST_RATE`

### Requirement: Once-per-request resolution of shared dependencies
Within a single request, each metric in the dependency closure SHALL be resolved exactly once, regardless of how many requested metrics depend on it. Cross-request caching SHALL NOT be performed.

#### Scenario: Shared dependency resolved once
- **WHEN** `TOTAL_PROFIT` and `TOTAL_INTEREST_RATE` are requested together
- **THEN** their shared dependencies (position records, paired positions, `TOTAL_INSTRUMENT_VALUE`) are each resolved exactly once for the request

#### Scenario: No state leaks between requests
- **WHEN** two consecutive requests ask for the same metrics
- **THEN** each request performs its own resolution from the current database state

### Requirement: Full-series dependency access
A metric's resolver SHALL receive the complete bucketed series of each of its dependencies' outputs for the requested interval, not only same-bucket values.

#### Scenario: Previous-bucket access
- **WHEN** `CURRENT_INTEREST_RATE` is resolved for bucket N
- **THEN** its resolver can read the `TOTAL_INSTRUMENT_VALUE` series value for bucket N−1 to use as a synthetic opening position

### Requirement: Uniform request parameters
The request parameters — interval, granularity, record filters, `groupBy`, and target currency — SHALL apply uniformly to every metric in the dependency closure of a single request.

#### Scenario: Grouped resolution propagates to dependencies
- **WHEN** metrics are requested with `groupBy: FUND`
- **THEN** all resolved metrics in the closure produce outputs partitioned by the same fund group keys

#### Scenario: Leaf record queries are type-specific and executed once
- **WHEN** metrics with different record needs are requested together (e.g. `BALANCE` and `TOTAL_PROFIT`)
- **THEN** each leaf record-set metric in the closure issues its own repository query scoped to the transaction types its consumers need, and each such query is executed at most once per request
