# metric-report-api (delta)

## MODIFIED Requirements

### Requirement: Multi-metric report endpoint
The analytics service SHALL expose `POST /funds-api/analytics/v1/metrics` accepting a request with a non-empty list of metric names, a nested `interval` (granularity, from, to — with from strictly before to), an optional nested `filter` (funds, units), an optional `grouping` criteria, and a target currency. The response SHALL contain the resolved time buckets and one series per requested metric.

#### Scenario: Single metric request
- **WHEN** a client requests `["TOTAL_PROFIT"]` for a monthly interval with a target currency
- **THEN** the response contains the bucket boundaries and one `TOTAL_PROFIT` series with one value per bucket

#### Scenario: Multi-metric request
- **WHEN** a client requests `["TOTAL_PROFIT", "TOTAL_INTEREST_RATE"]` in one call
- **THEN** the response contains exactly two series, one per requested metric, over the same buckets

#### Scenario: Grouped request
- **WHEN** a client requests metrics with `grouping: FUND`
- **THEN** each series contains per-group values keyed by the group key for every bucket

#### Scenario: Invalid interval
- **WHEN** a client sends an `interval` whose `from` is not strictly before its `to`
- **THEN** the service responds 400
