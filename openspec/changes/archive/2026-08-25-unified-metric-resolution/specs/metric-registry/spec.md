# metric-registry

## ADDED Requirements

### Requirement: Metric definitions
The analytics service SHALL define each metric with a unique name, an output type, a list of dependency metric names, and a resolver. Metrics with output type `BucketedScalars` SHALL additionally declare a unit type of `CURRENCY` or `PERCENTAGE` and are exposed; metrics with any other output type (`RecordSet`, `CashFlows`, `UnitAmountSeries`) are internal and SHALL NOT declare a unit type.

#### Scenario: Exposed metric definition
- **WHEN** the registry is inspected for `TOTAL_PROFIT`
- **THEN** the definition has output type `BucketedScalars`, unit type `CURRENCY`, and dependencies referencing other registered metrics

#### Scenario: Internal metric definition
- **WHEN** the registry is inspected for the paired-positions metric
- **THEN** the definition has a non-scalar output type, no unit type, and is marked internal

### Requirement: Startup validation of the metric graph
The analytics service SHALL validate the metric registry at application startup: every declared dependency MUST reference a registered metric, and the dependency graph MUST be acyclic. Validation failure SHALL prevent the application from starting.

#### Scenario: Missing dependency fails startup
- **WHEN** a metric declares a dependency on a name not present in the registry
- **THEN** application startup fails with an error identifying the metric and the missing dependency

#### Scenario: Cyclic dependency fails startup
- **WHEN** the registry contains metrics whose dependencies form a cycle
- **THEN** application startup fails with an error identifying the cycle

### Requirement: Metric discovery endpoint
The analytics service SHALL expose `GET /funds-api/analytics/v1/metrics` returning the exposed metrics with their name and unit type. Internal metrics SHALL NOT appear in the response.

#### Scenario: Listing exposed metrics
- **WHEN** a client calls `GET /funds-api/analytics/v1/metrics`
- **THEN** the response contains exactly the ten exposed metrics, each with `metric` and `unit` (`CURRENCY` or `PERCENTAGE`)

#### Scenario: Internal metrics are hidden
- **WHEN** a client calls `GET /funds-api/analytics/v1/metrics`
- **THEN** no internal metric (record sets, paired positions, holdings) appears in the response
