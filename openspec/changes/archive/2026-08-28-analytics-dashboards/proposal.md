# Analytics Dashboards

## Why

Every analytics session starts from scratch: one localStorage-persisted query set on one machine, rebuilt by hand whenever a different view is wanted. The queries worth looking at — net worth by fund, investment performance, monthly spending — are stable; what's missing is a way to save them as named charts, group those charts into dashboards, and open a dashboard as a page that just renders. With multi-query charts and streaming rendering in place, dashboards are pure configuration: stored chart definitions replayed through the existing streaming endpoint.

## What Changes

- **Dashboard storage in the analytics service**: new `dashboard` and `dashboard_chart` tables (Flyway migration) in the analytics database. A dashboard belongs to a user and carries a name, a position (ordering across dashboards), and dashboard-level default settings (`default`-prefixed fields): granularity, a **relative lookback** (amount + unit, e.g. last 12 months), and target currency — defaults because the view page can override them at view time. A chart carries a name, a position within its dashboard, and its query list (metric, optional grouping, optional filter, client-generated query id) — charts inherit the dashboard's time and currency settings.
- **Dashboard CRUD API**: `GET/POST /funds-api/analytics/v1/dashboards`, `GET/PUT/DELETE /dashboards/{id}` (`PUT` = metadata: name + defaults), `PUT /dashboards/positions` to reorder dashboards, and granular chart endpoints: `POST /dashboards/{id}/charts` (append, the add-from-analytics seam), `PUT`/`DELETE /dashboards/{id}/charts/{chartId}`, `PUT /dashboards/{id}/charts/positions`. Charts carry queries with mandatory display labels. Per-user isolation via the standard user header; validation in TO init blocks.
- **Rendering is client-side replay**: no new resolution machinery. The dashboard page resolves the lookback to an absolute interval at view time (today minus lookback → today) and streams each chart through the existing `POST /metrics/stream` — charts fill progressively, one stream per chart.
- **Web client**:
  - Sidebar lists dashboards by name; each opens as its own page (`/dashboards/:id`).
  - Dashboard management (`/dashboards/:id/edit`): rename, edit settings, add/remove/reorder charts (position up/down), edit a chart's name and queries reusing the existing query editors; save issues one `PUT`. Dashboards can be created, deleted, and reordered (position up/down issuing one `PUT /positions`) from the list.
  - Analytics page gains an "Add to dashboard" action: pick a target dashboard, name the chart, and the current query list is appended via `POST /charts` (the chart adopts the dashboard's granularity/interval/currency).

Out of scope: sharing dashboards between users, per-chart setting overrides, chart types beyond the existing multi-series chart, server-side rendering or caching of dashboard data, absolute time ranges.

## Capabilities

### New Capabilities

- `dashboard-api`: dashboard and chart persistence contract — CRUD endpoints, per-user isolation, dashboard-level settings with relative lookback, chart append, ordering, validation rules.

### Modified Capabilities

None. `metric-report-api`, `metric-stream-api`, and the resolution capabilities are consumed unchanged.

## Impact

- **analytics-api**: new TOs — `DashboardTO`, `DashboardChartTO`, `DashboardLookbackTO`, request TOs `CreateDashboardTO`/`UpdateDashboardTO`/`CreateDashboardChartTO`, lookback unit enum; default settings fields carry the `default` prefix.
- **analytics-service**: Flyway migration `V3__add_dashboard_tables.sql`; `DashboardRepository` (Exposed), `DashboardService`, `dashboardApiRouting`; Koin wiring in `AnalyticsDependencies`.
- **Tests**: dashboard API test (CRUD round-trip, chart append, reorder via PUT, per-user isolation, validation 400s, 404s).
- **client/web-client**: `dashboardApi.ts`; sidebar dashboard list; dashboard view page (lookback resolution + one stream per chart, reusing the chart assembly extracted from `AnalyticsPage`); dashboard management page; "Add to dashboard" dialog on the analytics page.
- **No impact**: engine, streaming endpoint, other services, Android client.
