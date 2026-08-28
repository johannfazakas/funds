# Design — analytics-dashboards

## Context

Multi-query charts and the SSE streaming endpoint exist; the analytics page already authors exactly the artifact a dashboard chart needs (a named list of queries). Dashboards add a persistence and navigation layer on top: stored configuration in the analytics service, replayed through the unchanged streaming endpoint at view time. Two product decisions are fixed: time ranges are **relative lookbacks** resolved at view time, and granularity/interval/currency live at the **dashboard level** — charts define only name and queries.

## Goals / Non-Goals

**Goals:**
- Persist dashboards (name, settings, ordered charts) per user in the analytics service.
- Dashboard pages that render all charts progressively with zero new resolution machinery.
- Full management: create/rename/delete dashboards; add/edit/remove/reorder charts; append the analytics page's current queries as a chart.

**Non-Goals:**
- Sharing/multi-user dashboards, per-chart setting overrides, absolute time ranges, new chart types, server-side caching of chart data, drag-and-drop reordering (position buttons suffice).

## Decisions

### 1. Data model: dashboard-level settings, JSON queries per chart

Two tables (Flyway `V3__add_dashboard_tables.sql`):

- `dashboard`: `id` (uuid), `user_id`, `name`, `position` (int, sidebar order), `default_granularity`, `default_lookback_amount` (int), `default_lookback_unit`, `default_target_currency` — the `default_` prefix marks these as view-time-overridable defaults.
- `dashboard_chart`: `id` (uuid), `dashboard_id` (FK, cascade delete), `name`, `position` (int, order within dashboard), `queries` (jsonb — the serialized `List<DashboardQueryTO>`, each query carrying a mandatory display `label` used in the chart legend and editors; the UI autogenerates it from the metric/grouping/filters until the user overrides it).

Queries are stored as a JSON column, not normalized rows: they are always read and written as a unit with their chart, their shape will evolve with the query model, and nothing queries into them server-side. Chart rows (rather than one JSON blob per dashboard) exist so `POST /charts` can append without rewriting the dashboard and positions stay explicit.

### 2. Relative lookback, resolved client-side

`lookback = { amount: Int > 0, unit: DAY | WEEK | MONTH | YEAR }` (UNIX_CASE enum). The **client** resolves it when opening a dashboard: `to = today`, `from = today - amount×unit`, then calls the existing streaming endpoint with that absolute interval. The server stores but never interprets lookbacks — no new time logic in the service, and the streaming/report APIs stay untouched. Charts inherit the dashboard's resolved interval, granularity, and currency.

### 3. API surface

```
GET    /funds-api/analytics/v1/dashboards               → dashboards with charts, ordered by position
POST   /funds-api/analytics/v1/dashboards               → create (name, settings, optional initial charts)
GET    /funds-api/analytics/v1/dashboards/{id}          → one dashboard with charts
PUT    /funds-api/analytics/v1/dashboards/{id}          → update metadata: name + default settings (charts untouched)
PUT    /funds-api/analytics/v1/dashboards/positions     → reorder: full ordered id list, positions reassigned 0..n-1
DELETE /funds-api/analytics/v1/dashboards/{id}          → delete with charts
POST   /funds-api/analytics/v1/dashboards/{id}/charts             → append one chart, position = end
PUT    /funds-api/analytics/v1/dashboards/{id}/charts/{chartId}   → replace a chart's name and queries (id + position kept)
DELETE /funds-api/analytics/v1/dashboards/{id}/charts/{chartId}   → delete one chart
PUT    /funds-api/analytics/v1/dashboards/{id}/charts/positions   → reorder charts (same exactly-once rule)
```

Dashboard reordering mirrors chart ordering one level up: `position` stays a server-owned column on the `dashboard` row, and the only cross-row writer is `PUT /positions`, which requires each of the user's dashboard ids exactly once and renumbers them in one transaction (missing/unknown/duplicate ids → 400, order untouched). Create keeps appending at the end; delete leaves a harmless gap that the next reorder compacts.

Charts are managed granularly (append, per-chart update/delete, positions reorder) so the chart edit view and the management page act immediately without racing each other; the dashboard `PUT` shrank to metadata only. List returns charts inline — dashboards are small, and the sidebar/list/view all want them.

Request TOs follow the codebase's per-operation convention: `CreateDashboardTO` (POST) and `UpdateDashboardTO` (PUT), both carrying name + default settings (`defaultGranularity`, `defaultLookback`, `defaultTargetCurrency`) + charts as `CreateDashboardChartTO`; `position` is server-owned (create appends at the end, chart order comes from list order) and thus absent from requests. Validation (TO init blocks, consistent with the metrics TOs): non-blank dashboard and chart names, lookback amount > 0, each chart has at least one query, query ids unique within a chart, non-blank query ids. Ownership: every read/write scopes by the `FUNDS_USER_ID` header; a dashboard belonging to another user is indistinguishable from absent (404).

### 4. Service structure

Mirrors the existing layout: `DashboardRepository` (Exposed, owns both tables, transactional replace), `DashboardService` (ownership checks, position assignment on append), `web/DashboardApiRouting.kt`, wiring in `AnalyticsDependencies`. Domain stays thin — dashboards are configuration, so TOs map to a small `Dashboard`/`DashboardChart` domain model without behavior.

### 5. Web client

- **`dashboardApi.ts`**: typed CRUD client mirroring the endpoint list.
- **Sidebar**: a Dashboards section listing names (ordered), each linking to `/dashboards/:id`; a manage link to `/dashboards` (list page with create/delete/open).
- **Dashboard view** (`/dashboards/:id`): shows live view controls — granularity, from/to local dates, currency — initialized from the stored defaults (the from/to dates are derived by resolving `defaultLookback` against today; the lookback itself is edited only on the management page). Changing any control re-streams every chart with the new settings, view-time only (the stored values remain the defaults). For each chart the page opens one `streamMetricsReport` and renders a `MultiSeriesChart` — the incremental merge logic and label building move out of `AnalyticsPage` into a shared `chartAssembly.ts` (report state builder + line/label/color derivation) so both pages consume identical rendering. Charts stream concurrently; each chart shows its own spinner until its `buckets` event and its own error state (one failed chart does not blank the others — the all-or-nothing rule applies within a chart's request, not across charts).
- **Dashboard management** (`/dashboards/:id/edit`): dashboard name + default settings with one Save (metadata `PUT`); below, the chart list only — each row shows name + query labels with immediate up/down (charts positions `PUT`), delete (chart `DELETE`, confirmed), and edit (navigates to the chart edit page); "Add chart" appends a default chart and opens it for editing.
- **Chart edit** (`/dashboards/:id/charts/:chartId/edit`, also reachable from the pencil on each dashboard-view chart card): chart name, the query editors (with per-query label inputs), and a live preview streamed with the dashboard's defaults; Save issues the chart `PUT`. Query labels replace the old A/B/C notation everywhere — legend lines read `label` (grouped: `label — group`) — and are autogenerated from metric/grouping/filters until manually edited.
- **Analytics page**: an "Add to dashboard" button opens a dialog (dashboard select + chart name); confirm posts the page's current query list to `/charts`. Only queries are carried — the dialog states that the chart will use the dashboard's period and currency.

## Risks / Trade-offs

- **PUT full-replace loses concurrent edits** (last write wins). Accepted for a single-user tool; the append endpoint covers the one flow where two writers are plausible (analytics page + open editor).
- **JSON query column** means query-shape changes need data migration or tolerant deserialization. Mitigated by using the API TO shape (already versioned by the OpenSpec contract) and `ignoreUnknownKeys` on read.
- **N concurrent streams** on a chart-heavy dashboard (N×queries SSE connections). Browsers cap ~6 connections per host on HTTP/1.1 — charts beyond the cap queue behind earlier ones and still render; acceptable, noted for a future HTTP/2 or batching pass.
- **Client-resolved lookback** means "today" is the browser's date; a service-side interpretation would need timezone policy. Fine for a single-user local tool.
