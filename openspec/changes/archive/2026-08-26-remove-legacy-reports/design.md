# Design — remove-legacy-reports

## Context

Analytics-service currently serves two generations of reporting: the legacy per-report endpoints (`AnalyticsApiRouting` → `AnalyticsService`/`PerformanceService`/`InterestRateService`, each with whole-series logic) and the unified metrics endpoint (`MetricsApiRouting` → `MetricResolutionService` over the series graph). The metrics endpoint is verified equivalent by `MetricsParityTest`, which literally calls both generations and compares numbers. A separate, older `service/reporting` module tree predates analytics-service entirely; it is compiled by Gradle but referenced by nothing and not deployed (docker-compose block commented out).

## Goals / Non-Goals

**Goals:**
- Single reporting code path: the series engine.
- No net loss of test coverage — parity expectations survive as fixed values.
- Remove the reporting modules and every trace of their wiring (gradle, compose, DB bootstrap).
- Retire the five legacy capability specs (currently the only specs failing `openspec validate`).

**Non-Goals:**
- No behavior or API change to the metrics endpoint or its TOs.
- No touching of ingestion (`TransactionsCreatedHandler`), the analytics DB schema, or existing local databases (`ff_reporting` stays orphaned locally; only its bootstrap is removed).
- No removal of shared domain kept alive by the series engine (`InterestRateCalculator`, `ReportInterval`, grouping helpers, `InvestmentPosition`).

## Decisions

1. **Coverage first, deletion second.** `MetricsParityTest`'s scenarios (metric set × grouping × currency over seeded data) are re-expressed in `MetricsApiTest` with literal expected values taken from the current green run. Only then are the legacy endpoints deleted. This preserves the regression net the parity suite provided, minus the ability to re-derive expectations — acceptable, since the legacy implementations were themselves pinned by their own tests whose values came from the same seeds.
2. **Deletion order: tests → routing → services → TOs → repository pruning.** Working top-down keeps the module compiling at each step; repository methods are pruned last, guided by "zero references after service deletion" (expected candidates: raw grouped-report queries used only by the legacy services; the series leaves use `getUnitAmountsBefore`/`getBucketedUnitAmounts`/`getRecords`/`getRecordsBefore`, which stay).
3. **Reporting tree removed wholesale with `git rm`** (history preserves it). Gradle includes deleted; local-only cleanup limited to the commented compose block and the `ff_reporting` lines in `infra/local/volumes/postgres/init/*.sql`. No migration/teardown of existing local databases.
4. **Spec removal via delta specs.** Each of the five legacy capabilities gets a `## REMOVED Requirements` delta naming every requirement it retires, so the archive sync deletes the main spec files. This also clears the standing `openspec validate` failures.
5. **`TimeGranularity` and `GroupingCriteria` stay in analytics-api** — the metrics request uses both. `AnalyticsReportTO`'s generic envelope goes; nothing outside the deleted services references it.

## Risks / Trade-offs

- **Losing the executable equivalence oracle**: after this change, "do the numbers match the old implementation" can no longer be re-run. Mitigated by freezing the parity values into `MetricsApiTest` before deletion; the git history retains the legacy code if a future dispute needs re-derivation.
- **Hidden consumers of the legacy endpoints**: no live consumers exist (web client and SDK target `/v1/metrics` only); the reporting modules did have dead client-side code (`ReportingClient` in client-sdk, budget-chart TS files in web-client), which is deleted with them. The notebook client consumes the published `reporting-sdk` maven artifact and keeps compiling; its report-view flows were already non-functional (service not deployed) and their removal is deferred.
- **Repository pruning overreach**: deleting a query the series engine actually uses would break tests immediately — the full analytics suite is the gate after each deletion step.
