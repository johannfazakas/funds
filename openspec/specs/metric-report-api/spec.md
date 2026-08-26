# Metric Report API

## Purpose

Defines the multi-metric report endpoint of the analytics service: request shape, response series with unit types and target-currency handling, and validation of requested metric names.

## Requirements

### Requirement: Multi-metric report endpoint
The analytics service SHALL expose `POST /funds-api/analytics/v1/metrics` accepting a request with a non-empty list of queries — each query carrying a client-supplied opaque id (unique within the request), a metric name, and optionally its own `grouping` criteria and `filter` (funds, units) — plus a request-level nested `interval` (granularity, from, to — with from strictly before to) and a target currency that apply to every query. The response SHALL contain the resolved time buckets and one series per query, in query order, echoing the query's id; the same metric MAY appear in multiple queries, and queries with identical parameters SHALL still yield one series each, distinguished by id. A request with duplicate query ids SHALL be rejected with HTTP 400.

#### Scenario: Single query request
- **WHEN** a client sends one query for `TOTAL_PROFIT` over a monthly interval with a target currency
- **THEN** the response contains the bucket boundaries and one `TOTAL_PROFIT` series with one value per bucket

#### Scenario: Multi-query request with distinct contexts
- **WHEN** a client sends one query for `BALANCE` grouped by `FUND` and another for `BALANCE` ungrouped and filtered to one fund
- **THEN** the response contains exactly two series over the same buckets, each identifying its query, the first with per-fund groups and the second with a single ungrouped series

#### Scenario: Per-query grouping
- **WHEN** a query specifies `grouping: FUND`
- **THEN** that query's series contains per-group values keyed by the group key for every bucket, regardless of other queries' grouping

#### Scenario: Identical queries yield distinct series
- **WHEN** two queries with different ids carry the same metric, grouping, and filter
- **THEN** the response contains two series with equal values, one per query id

#### Scenario: Duplicate query ids rejected
- **WHEN** a client sends two queries sharing the same id
- **THEN** the service responds 400

#### Scenario: Invalid interval
- **WHEN** a client sends an `interval` whose `from` is not strictly before its `to`
- **THEN** the service responds 400

### Requirement: Unit type in responses
Each series in the response SHALL carry the metric's unit type. Series with unit type `CURRENCY` SHALL be expressed in the request's target currency; series with unit type `PERCENTAGE` are dimensionless and SHALL NOT depend on the target currency.

#### Scenario: Currency series carries target currency
- **WHEN** `BALANCE` is requested with target currency RON
- **THEN** the `BALANCE` series is marked `CURRENCY` and its values are RON amounts

#### Scenario: Percentage series ignores target currency
- **WHEN** `TOTAL_INTEREST_RATE` is requested with different target currencies under constant conversion rates
- **THEN** the series is marked `PERCENTAGE` and its values are equal within the interest rate calculator's precision regardless of the requested target currency

### Requirement: Invalid metric requests are rejected
The endpoint SHALL reject with HTTP 400 any request in which any query names a metric that is unknown or internal, identifying the offending metric names. It SHALL also reject an empty query list.

#### Scenario: Unknown metric in one query
- **WHEN** a client sends queries for `TOTAL_PROFIT` and `BOGUS_METRIC`
- **THEN** the service responds 400 and the error names `BOGUS_METRIC`, without resolving any query

#### Scenario: Internal metric
- **WHEN** a query names the internal paired-positions metric
- **THEN** the service responds 400 without resolving any queries

#### Scenario: Empty query list
- **WHEN** a client sends an empty `queries` array
- **THEN** the service responds 400
