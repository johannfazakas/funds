# Metric Registry

## Purpose

Defines how the analytics service declares metrics as named, typed definitions with dependencies and resolvers, validates the resulting metric graph at startup, and exposes discoverable metric metadata to clients.

## Requirements

### Requirement: Metric definitions
The analytics service SHALL declare metrics as a closed, typed domain hierarchy in which each metric carries its slice (output) type as a compile-time type parameter, and external metrics are exactly those carrying an API metric mapping (with its `CURRENCY` or `PERCENTAGE` unit type); internal metrics (record sets, unit amount series, cash flows) carry no API mapping and are not addressable by clients. Each metric SHALL have exactly one registered definition binding it to its typed resolver factory and its dependencies, referenced as domain metrics (not strings). A resolver SHALL be unable to produce a slice of a different type than its metric declares, and requesting a dependency's slice under a wrong type SHALL be a compile-time error.

#### Scenario: External metric definition
- **WHEN** the registry is inspected for the total-profit metric
- **THEN** its definition produces scalar slices, maps to the `TOTAL_PROFIT` API metric with unit type `CURRENCY`, and its dependencies reference other registered metrics

#### Scenario: Internal metric definition
- **WHEN** the registry is inspected for the paired-positions metric
- **THEN** its definition produces position slices and has no API metric mapping, so it cannot be requested by clients

### Requirement: Startup validation of the metric graph
The analytics service SHALL validate the metric registry at application startup: every domain metric MUST have exactly one registered definition (no missing, no duplicate), every declared dependency MUST reference a registered metric, and the dependency graph MUST be acyclic. Validation failure SHALL prevent the application from starting.

#### Scenario: Metric without definition fails startup
- **WHEN** a domain metric has no registered definition
- **THEN** application startup fails with an error identifying the missing metric

#### Scenario: Missing dependency fails startup
- **WHEN** a metric declares a dependency whose definition is not registered
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
