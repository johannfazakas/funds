# dashboard-api (delta)

## ADDED Requirements

### Requirement: Dashboard persistence and retrieval
The analytics service SHALL persist per-user dashboards, each carrying a name, a position among the user's dashboards, dashboard-level default settings — explicitly named as defaults (`defaultGranularity`, a relative `defaultLookback` of amount and unit, and `defaultTargetCurrency`) since the view page may override them at view time — and an ordered list of charts. Each chart carries an id, a name, a position within the dashboard, and a non-empty list of queries (`DashboardQueryTO`: client-supplied query id, mandatory non-blank display label, metric, optional grouping, optional filter). The service SHALL expose `GET /funds-api/analytics/v1/dashboards` returning the user's dashboards with their charts inline, ordered by position, and `GET /funds-api/analytics/v1/dashboards/{id}` returning a single dashboard with its charts ordered by position.

#### Scenario: Listing dashboards with charts
- **WHEN** a user with two dashboards requests the dashboard list
- **THEN** both dashboards are returned in position order, each with its charts, default settings, and default lookback

#### Scenario: Charts preserve query definitions
- **WHEN** a dashboard chart was saved with queries carrying grouping and fund filters
- **THEN** retrieving the dashboard returns the same query ids, metrics, groupings, and filters

#### Scenario: Lookback is stored, not resolved
- **WHEN** a dashboard with a 12-month lookback is retrieved on different days
- **THEN** the response carries the lookback amount and unit unchanged, with no absolute dates

### Requirement: Dashboard lifecycle
The service SHALL support creating a dashboard (`POST /dashboards` with name, default settings, and optional initial charts, modeled as `CreateDashboardTO`), updating its metadata (`PUT /dashboards/{id}` with `UpdateDashboardTO`, replacing name and default settings without touching charts), and deleting it (`DELETE /dashboards/{id}` removing the dashboard and its charts).

#### Scenario: Create and retrieve round-trip
- **WHEN** a client creates a dashboard with settings and one chart
- **THEN** the create response and a subsequent GET return the dashboard with a generated id and the chart as submitted

#### Scenario: Metadata update leaves charts untouched
- **WHEN** a client PUTs a dashboard with a new name and defaults
- **THEN** the response carries the new metadata and the same charts as before

#### Scenario: Delete removes charts
- **WHEN** a client deletes a dashboard
- **THEN** subsequent GETs of the dashboard respond 404 and its charts are no longer stored

### Requirement: Chart lifecycle
The service SHALL manage charts individually: `PUT /dashboards/{id}/charts/{chartId}` (`UpdateDashboardChartTO`) replaces a chart's name and queries while keeping its id and position; `DELETE /dashboards/{id}/charts/{chartId}` removes only that chart; `PUT /dashboards/{id}/charts/positions` (`UpdateDashboardChartPositionsTO`) reorders the dashboard's charts under the same exactly-once rule as dashboard reordering. An unknown or foreign chart id SHALL respond 404.

#### Scenario: Chart update keeps identity and position
- **WHEN** a client updates the second of two charts with a new name and queries
- **THEN** the chart keeps its id and position and a subsequent GET returns the edited content

#### Scenario: Chart delete is isolated
- **WHEN** a client deletes one of two charts
- **THEN** the other chart remains unchanged

#### Scenario: Chart reorder validates completeness
- **WHEN** a reorder request omits one of the dashboard's charts
- **THEN** the service responds 400 and the order is unchanged

### Requirement: Dashboard reordering
The service SHALL expose `PUT /dashboards/positions` accepting the ordered list of the user's dashboard ids (`UpdateDashboardPositionsTO`), reassigning positions 0..n-1 in list order atomically and returning the reordered dashboards. The request SHALL be rejected with HTTP 400 unless it references each of the requesting user's dashboards exactly once — missing, unknown, foreign, or duplicate ids all fail without changing the stored order.

#### Scenario: Reorder compacts positions
- **WHEN** a user with three dashboards submits their ids in a new order
- **THEN** the response and subsequent listing return the dashboards in that order with positions 0, 1, 2

#### Scenario: Incomplete reorder is rejected
- **WHEN** a reorder request omits one of the user's dashboards, duplicates an id, or includes an id that is not theirs
- **THEN** the service responds 400 and the existing order is unchanged

### Requirement: Chart append
The service SHALL expose `POST /dashboards/{id}/charts` appending a single chart (name and queries) at the end of the dashboard's chart list without modifying existing charts, returning the created chart with its generated id and position.

#### Scenario: Appending places the chart last
- **WHEN** a chart is appended to a dashboard that already has two charts
- **THEN** the new chart receives the next position and the existing charts are unchanged

### Requirement: Validation and per-user isolation
The service SHALL reject with HTTP 400 any dashboard or chart payload with a blank name, a lookback amount that is not strictly positive, a chart with no queries, blank or duplicate query ids within a chart, a blank query label, or an unknown metric name. All dashboard operations SHALL be scoped to the requesting user: a dashboard id belonging to another user SHALL be treated as absent (HTTP 404) for every operation.

#### Scenario: Invalid payloads rejected
- **WHEN** a client creates a dashboard with a blank name or a chart with an empty query list
- **THEN** the service responds 400 naming the violation

#### Scenario: Foreign dashboard is invisible
- **WHEN** a user issues GET, PUT, DELETE, or chart append against another user's dashboard id
- **THEN** the service responds 404 and the other user's dashboard is unchanged
