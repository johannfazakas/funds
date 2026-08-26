# metric-resolution (delta)

## MODIFIED Requirements

### Requirement: Once-per-request resolution of shared dependencies
Within a single request, each resolution node — identified by its series together with its projected query context (grouping and normalized filter, reduced to the dimensions the series is sensitive to) — SHALL be resolved exactly once, regardless of how many queries or dependent series consume it. Nodes with distinct projected contexts SHALL be resolved independently even for the same series. Cross-request caching SHALL NOT be performed.

#### Scenario: Shared dependency resolved once within a query
- **WHEN** one query's closure reaches the same node through multiple paths (e.g. `TOTAL_PROFIT` via value and investment)
- **THEN** that node is resolved exactly once for the request

#### Scenario: Identical contexts shared across queries
- **WHEN** two queries request different metrics with equal grouping and equal (normalized) filters
- **THEN** the nodes their closures have in common are each resolved exactly once

#### Scenario: Different filters are isolated
- **WHEN** two queries request the same metric with different fund filters
- **THEN** every node in each closure resolves separately and neither query observes the other's data

#### Scenario: No state leaks between requests
- **WHEN** two consecutive requests contain the same queries
- **THEN** each request performs its own resolution from the current database state

### Requirement: Uniform request parameters
The interval, granularity, and target currency SHALL apply uniformly to every node in the request — all queries share one bucket clock. Grouping and record filters SHALL be per query: each query's context propagates unchanged down its dependency closure, so every node in a query's closure resolves under that query's grouping and filter.

#### Scenario: Grouped resolution propagates to dependencies
- **WHEN** a query requests a metric with `grouping: FUND`
- **THEN** all grouping-sensitive nodes in that query's closure produce outputs partitioned by the same fund group keys

#### Scenario: Shared bucket clock across queries
- **WHEN** multiple queries with different groupings and filters are resolved in one request
- **THEN** every query's series is aligned to the same bucket sequence derived from the request interval

#### Scenario: Leaf record queries are context-scoped and executed once
- **WHEN** queries with different record needs are resolved together
- **THEN** each leaf node issues its own repository query scoped to its transaction types and its projected context, and each such query is executed at most once per request

## ADDED Requirements

### Requirement: Context projection for cross-query sharing
Each series definition SHALL explicitly declare which query-context dimensions (grouping, filter) affect its output. Node identity SHALL use the query context projected onto the declared dimensions, so queries differing only in dimensions a node ignores share that node's single resolution. Semantically equal filters (ordering, duplicates, empty versus absent) SHALL produce the same node identity.

#### Scenario: Grouping-insensitive chain shared across groupings
- **WHEN** one query requests an investment metric grouped by `FUND` and another requests an interest-rate metric ungrouped, with equal filters
- **THEN** the grouping-insensitive record and position nodes they share are resolved exactly once, while the grouping-sensitive nodes resolve separately per query

#### Scenario: Normalized filters collide
- **WHEN** two queries carry filters that are equal up to ordering or empty-versus-absent representation
- **THEN** their closures share nodes as if the filters were written identically

#### Scenario: Failure in a shared node fails the request
- **WHEN** a node shared by several queries fails
- **THEN** the whole resolution is cancelled and the request errors — no query returns partial results
