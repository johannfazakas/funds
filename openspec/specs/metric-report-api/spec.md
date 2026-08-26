# Metric Report API

## Purpose

Defines the multi-metric report endpoint of the analytics service: request shape, response series with unit types and target-currency handling, and validation of requested metric names.

## Requirements

### Requirement: Multi-metric report endpoint
The analytics service SHALL expose `POST /funds-api/analytics/v1/metrics` accepting a request with a non-empty list of metric names, granularity, time interval, optional record filters (funds, units), optional `groupBy`, and a target currency. The response SHALL contain the resolved time buckets and one series per requested metric.

#### Scenario: Single metric request
- **WHEN** a client requests `["TOTAL_PROFIT"]` for a monthly interval with a target currency
- **THEN** the response contains the bucket boundaries and one `TOTAL_PROFIT` series with one value per bucket

#### Scenario: Multi-metric request
- **WHEN** a client requests `["TOTAL_PROFIT", "TOTAL_INTEREST_RATE"]` in one call
- **THEN** the response contains exactly two series, one per requested metric, over the same buckets

#### Scenario: Grouped request
- **WHEN** a client requests metrics with `groupBy: FUND`
- **THEN** each series contains per-group values keyed by the group key for every bucket

### Requirement: Unit type in responses
Each series in the response SHALL carry the metric's unit type. Series with unit type `CURRENCY` SHALL be expressed in the request's target currency; series with unit type `PERCENTAGE` are dimensionless and SHALL NOT depend on the target currency.

#### Scenario: Currency series carries target currency
- **WHEN** `BALANCE` is requested with target currency RON
- **THEN** the `BALANCE` series is marked `CURRENCY` and its values are RON amounts

#### Scenario: Percentage series ignores target currency
- **WHEN** `TOTAL_INTEREST_RATE` is requested with different target currencies under constant conversion rates
- **THEN** the series is marked `PERCENTAGE` and its values are equal within the interest rate calculator's precision regardless of the requested target currency

### Requirement: Invalid metric requests are rejected
The endpoint SHALL reject with HTTP 400 any request naming a metric that is unknown or internal, identifying the offending metric names. It SHALL also reject an empty metric list.

#### Scenario: Unknown metric
- **WHEN** a client requests `["TOTAL_PROFIT", "BOGUS_METRIC"]`
- **THEN** the service responds 400 and the error names `BOGUS_METRIC`

#### Scenario: Internal metric
- **WHEN** a client requests the internal paired-positions metric by name
- **THEN** the service responds 400 without resolving any metrics

#### Scenario: Empty metric list
- **WHEN** a client sends an empty `metrics` array
- **THEN** the service responds 400
