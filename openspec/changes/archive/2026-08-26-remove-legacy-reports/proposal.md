# Remove Legacy Reports

## Why

The unified metrics endpoint (`POST /funds-api/analytics/v1/metrics`) is now the single reporting surface: it covers everything the four legacy analytics report endpoints did, the web client uses only it, and the streaming series engine behind it has been verified bit-identical against the legacy outputs by parity tests. The legacy paths are dead weight that duplicates logic (its own resolvers, its own `toInvestmentPositions`, its own report assembly) and every analytics refactor pays a tax keeping them compiling.

Separately, the `service/reporting` module tree (reporting-api, reporting-sdk, reporting-service) is a fully superseded predecessor of analytics-service: nothing references it outside `settings.gradle.kts` and a commented-out docker-compose block, and its container is not even started locally.

## What Changes

- **Fold parity coverage into the metrics tests first**: `MetricsParityTest` compares the metrics endpoint against the legacy endpoints, so it dies with them. Before removal, its scenarios' expected values are captured as fixed assertions in `MetricsApiTest` so coverage does not drop.
- **Remove legacy analytics report endpoints**: `AnalyticsApiRouting` (balance, net-change, performance, interest-rate routes) and its wiring in `AnalyticsRouting`.
- **Remove legacy analytics services**: `AnalyticsService` (report logic), `PerformanceService`, `InterestRateService`, and their unit/integration tests. Ingestion (`TransactionsCreatedHandler`) and shared domain (`InterestRateCalculator`, `ReportInterval`, grouping helpers, repository) stay.
- **Remove legacy API models** from analytics-api: `AnalyticsReportTO`, `PerformanceDataTO`, `InterestRateDataTO` (used only by the removed services). Shared models (`TimeGranularity`, `GroupingCriteria`, metrics TOs) stay.
- **Remove the reporting module tree**: `service/reporting/*` (api, sdk, service), its `settings.gradle.kts` includes, the commented docker-compose block, and the `ff_reporting` database bootstrap in the local postgres init scripts.
- **Retire legacy capability specs**: the five capabilities describing the removed endpoints are deleted from `openspec/specs`.
- **Prune now-dead repository queries**: any `AnalyticsRecordRepository` methods and domain types used only by the removed services.

No behavior change for any consumer: the metrics endpoint, its TOs, the web client, and event ingestion are untouched.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `metric-catalog`: the "Parity with legacy report semantics" requirement defines metric values by reference to the legacy report specs; it is replaced by a self-contained "Metric calculation semantics" requirement so the legacy specs can be deleted.
- `analytics-report-model`: REMOVED — legacy generic report envelope is retired with the endpoints that used it.
- `ungrouped-performance-report`: REMOVED — superseded by the metric-report-api capability (TOTAL/CURRENT_INVESTMENT, values, profits).
- `grouped-performance-report`: REMOVED — superseded by metric-report-api grouping.
- `ungrouped-interest-rate-report`: REMOVED — superseded by TOTAL/CURRENT_INTEREST_RATE metrics.
- `grouped-interest-rate-report`: REMOVED — superseded by metric-report-api grouping.

## Impact

- **analytics-service**: `web/AnalyticsApiRouting.kt`, `service/AnalyticsService.kt`, `service/PerformanceService.kt`, `service/InterestRateService.kt` deleted; `config/AnalyticsRouting.kt` and `config/AnalyticsDependencies.kt` slimmed; `AnalyticsApiTest`, `AnalyticsServiceTest`, `PerformanceServiceTest`, `InterestRateServiceTest`, `MetricsParityTest` deleted after their coverage is folded into `MetricsApiTest`; unused repository queries pruned.
- **analytics-api**: `AnalyticsReportTO`, `PerformanceDataTO`, `InterestRateDataTO` deleted.
- **service/reporting**: entire directory tree deleted; `settings.gradle.kts` includes removed.
- **infra/local**: commented reporting-service block removed from docker-compose; `ff_reporting` dropped from postgres init scripts (existing local DBs unaffected; the database is simply orphaned).
- **openspec/specs**: five legacy capability spec directories deleted (these are also the specs currently failing validation).
- **client/client-sdk**: dead `ReportingClient` and its `reporting-api` gradle dependency deleted (zero callers).
- **client/web-client**: dead `BudgetChart.tsx`, `chartUtils.ts`, `types/reporting.ts` deleted (imported by nothing).
- **client/notebook**: untouched — it is a separate Gradle build consuming the published `reporting-sdk:1.0.0` maven artifact, which remains in the local repository; its report-view flows already target a service that was not deployed.
- **No impact**: other services, Kafka topics, analytics-sdk (targets the metrics endpoint).
