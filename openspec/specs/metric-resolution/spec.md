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

### Requirement: Progressive bucket emission
Every metric node SHALL emit its result progressively as a stream: one previous emission carrying its pre-interval state (possibly empty), followed by exactly one bucket slice per interval bucket, in bucket order. Dependent metrics SHALL consume dependency slices as they are emitted, without waiting for dependencies to complete their full series. The engine SHALL own this emission invariant; resolvers cannot emit out of order or skip buckets.

#### Scenario: Parent resolves buckets before dependencies finish
- **WHEN** a chain of metrics is resolved over a multi-bucket interval
- **THEN** a dependent metric's slice for bucket N is produced without requiring its dependencies to have emitted any bucket after N

#### Scenario: Empty buckets still emit
- **WHEN** a bucket contains no records for some node
- **THEN** that node still emits exactly one (empty) slice for the bucket, keeping all streams aligned to the interval's bucket sequence

### Requirement: Bucket-aligned dependency access
A resolver SHALL receive, for each bucket, only the same-bucket slices of its dependencies (and the dependencies' pre-interval state at seed time), regardless of how many dependencies it has. Cross-bucket needs SHALL be met by resolver-internal accumulator state, not by access to other buckets of a dependency.

#### Scenario: Cumulative metric via internal state
- **WHEN** `BALANCE` is resolved
- **THEN** its resolver accumulates a running balance seeded from the previous amounts and updated with each bucket's delta, receiving only the current bucket's amounts from its dependency

#### Scenario: Previous-bucket derivation via internal state
- **WHEN** `CURRENT_INTEREST_RATE` is resolved for bucket N
- **THEN** the previous bucket's valuation is available as state the resolver retained from bucket N−1 (seeded from the previous holdings valuation), not as an access into the dependency's series

### Requirement: Uniform per-bucket resolver contract
Metric resolvers SHALL implement a single contract with a previous-state step and a per-bucket step, receiving aligned dependency slices keyed by dependency name. Resolver instances SHALL be created per request via a factory so that any internal state is confined to one resolution. Whether a resolver keeps state, and how many dependencies it reads, SHALL NOT change the contract it implements.

#### Scenario: Stateless and stateful resolvers share the contract
- **WHEN** `NET_CHANGE` (stateless) and `BALANCE` (stateful) are defined
- **THEN** both implement the same per-bucket contract; statefulness is not visible in the registry or engine API

#### Scenario: State does not leak between requests
- **WHEN** two requests resolve the same metric concurrently
- **THEN** each request drives its own resolver instances and their accumulator states never interact

### Requirement: Concurrent streaming execution
The engine SHALL execute each node in the dependency closure as its own coroutine within a single per-request scope: buckets are processed strictly sequentially within a node, while independent nodes run concurrently. A node consumed by multiple dependents SHALL still execute exactly once, with its emissions shared to all consumers. A failure in any node SHALL cancel the whole resolution and propagate as the request's error.

#### Scenario: Independent branches run concurrently
- **WHEN** metrics from independent subgraphs are requested together
- **THEN** their nodes execute concurrently rather than one branch waiting for the other to complete

#### Scenario: Shared node executes once under fan-out
- **WHEN** two requested metrics share a dependency
- **THEN** the shared node's resolver runs once and both consumers observe its emissions

#### Scenario: Failure cancels the graph
- **WHEN** any node's resolution throws
- **THEN** all other nodes' coroutines are cancelled and the request fails with that error

### Requirement: Report assembly preserves existing responses
The service SHALL assemble the emission streams of the requested metrics into the same `MetricResolutionReport` (buckets + per-metric scalar series) as before, zero-filling groups for buckets that precede the group's first appearance. Externally observable responses and computed values SHALL remain identical to the whole-series implementation.

#### Scenario: Parity preserved
- **WHEN** the existing parity and integration suites run against the streaming engine
- **THEN** they pass unchanged, with values equal to the previous implementation in every bucket and group

#### Scenario: Late-appearing group is zero-backfilled
- **WHEN** a group first produces data in a mid-interval bucket
- **THEN** the assembled report contains that group with zero values for all earlier buckets

### Requirement: Uniform request parameters
The request parameters — interval, granularity, record filters, `groupBy`, and target currency — SHALL apply uniformly to every metric in the dependency closure of a single request.

#### Scenario: Grouped resolution propagates to dependencies
- **WHEN** metrics are requested with `groupBy: FUND`
- **THEN** all resolved metrics in the closure produce outputs partitioned by the same fund group keys

#### Scenario: Leaf record queries are type-specific and executed once
- **WHEN** metrics with different record needs are requested together (e.g. `BALANCE` and `TOTAL_PROFIT`)
- **THEN** each leaf record-set metric in the closure issues its own repository query scoped to the transaction types its consumers need, and each such query is executed at most once per request
