# Tasks — remove-legacy-reports

## 1. Freeze parity coverage

- [x] 1.1 Re-express `MetricsParityTest` scenarios in `MetricsApiTest` with literal expected values captured from the current green run (metric set × grouping × target currency over the seeded data)
- [x] 1.2 Run the analytics suite to confirm the new fixed-value assertions pass alongside the still-present parity tests

## 2. Remove legacy analytics endpoints and services

- [x] 2.1 Delete `MetricsParityTest`, `AnalyticsApiTest`, `AnalyticsServiceTest`, `PerformanceServiceTest`, `InterestRateServiceTest`
- [x] 2.2 Delete `web/AnalyticsApiRouting.kt` and its wiring in `config/AnalyticsRouting.kt`
- [x] 2.3 Delete `service/AnalyticsService.kt`, `service/PerformanceService.kt`, `service/InterestRateService.kt` and their Koin registrations in `config/AnalyticsDependencies.kt`
- [x] 2.4 Delete legacy TOs from analytics-api: `AnalyticsReportTO`, `PerformanceDataTO`, `InterestRateDataTO` (keep `TimeGranularity`, `GroupingCriteria`, metrics TOs)
- [x] 2.5 Prune `AnalyticsRecordRepository` methods and domain types with zero references after the deletions; run the analytics suite after each pruning step

## 3. Remove the reporting module tree

- [x] 3.1 `git rm -r service/reporting` and drop the three `service:reporting:*` includes from `settings.gradle.kts`
- [x] 3.2 Remove the commented reporting-service block from `infra/local/docker-compose.yml`
- [x] 3.3 Remove `ff_reporting` bootstrap lines from `infra/local/volumes/postgres/init/*.sql`

## 4. Specs and verification

- [x] 4.1 Verify `openspec validate --specs` passes for the remaining capabilities once deltas are synced at archive time (legacy capability failures disappear with their specs)
- [x] 4.2 Full build (`./gradlew build`) green with the reporting modules gone
- [x] 4.3 Rebuild and redeploy `ff_analytics`; smoke-test the metrics endpoint and the web client analytics page
- [x] 4.4 Update `MANAGEMENT.md`: drop the legacy-endpoint-removal follow-up note
