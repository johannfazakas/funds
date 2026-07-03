## Why

The analytics service supports balance and net-change reports but lacks performance and interest-rate analytics. Users cannot see how their investments perform over time, what their returns are, or break these metrics down by fund, account, financial unit, or category. Adding performance and interest-rate reports completes the analytics offering and lets users understand which specific investments drive their returns.

## What Changes

- Make AnalyticsReportTO generic with a type parameter to support different report data types
- Rename CURRENCY to FINANCIAL_UNIT in GroupingCriteria to reflect broader scope
- Split AnalyticsRecordFilter into AnalyticsInputRecordFilter (user-driven: fundIds, units) and AnalyticsDbRecordFilter (database layer: fundIds, units, transactionTypes)
- Introduce sealed GroupKey interface with per-criteria variants (Ungrouped, ByFund, ByAccount, ByFinancialUnit, ByCategory) for type-safe group key representation
- Unify grouped and ungrouped repository methods into single methods with optional groupBy parameter
- Add transactionTypes filter and raw record queries to the analytics repository
- Add InterestRateCalculator domain logic using time-weighted return with bisection
- Add PerformanceDataTO and InterestRateDataTO API models
- Add PerformanceService computing investment, profit, and instrument value metrics with unified grouped/ungrouped code path
- Add InterestRateService computing total and current interest rates with unified grouped/ungrouped code path
- Wire performance and interest-rate endpoints into the analytics API
- Add integration tests for performance and interest-rate endpoints
- Add performance and interest-rate reports with metric selector to the web client

## Capabilities

### New Capabilities

- `performance-report`: Performance analytics returning per-bucket time-series of totalInvestment, currentInvestment, totalProfit, currentProfit, totalInstrumentValue, and currencyValue metrics. Supports optional groupBy parameter to break down by fund, account, financial unit, or category.
- `interest-rate-report`: Interest-rate analytics returning per-bucket time-series of totalInterestRate and currentInterestRate computed via time-weighted return calculation. Supports optional groupBy parameter to break down by fund, account, financial unit, or category.

### Modified Capabilities

- `analytics-report-model`: AnalyticsReportTO made generic with type parameter `<T>` to support PerformanceDataTO and InterestRateDataTO alongside existing BalanceDataTO and NetChangeDataTO.
- `grouping-criteria`: CURRENCY renamed to FINANCIAL_UNIT to accurately reflect grouping by both currencies and instruments.
- `analytics-repository`: Added transactionTypes filter and raw record queries (getRecordsBefore, getRecords). Unified grouped and ungrouped aggregate methods into single methods with optional groupBy parameter. Introduced sealed GroupKey for type-safe group key representation.
- `analytics-domain`: Split AnalyticsRecordFilter into AnalyticsInputRecordFilter and AnalyticsDbRecordFilter. Unified domain value aggregates using GroupKey-keyed maps. Eliminated separate grouped/ungrouped domain types.
- `analytics-services`: Unified grouped and ungrouped code paths in AnalyticsService, PerformanceService, and InterestRateService. Eliminated dispatcher pattern — single method handles both cases via GroupKey abstraction.
- `web-client-analytics`: Added metric selector for performance (6 metrics) and interest-rate (2 metrics) report types, with extractMetric utility for single-metric charting.

## Impact

- **Analytics API**: AnalyticsReportTO becomes generic; new PerformanceDataTO and InterestRateDataTO models added; GroupingCriteria.CURRENCY renamed to FINANCIAL_UNIT; ungrouped reports now return `groupKey: "UNGROUPED"` instead of `null`
- **Analytics service**: New PerformanceService, InterestRateService, and InterestRateCalculator; sealed GroupKey interface with per-criteria variants; AnalyticsInputRecordFilter/AnalyticsDbRecordFilter split; unified repository methods with optional groupBy; eliminated dispatcher pattern in all services
- **Web client**: AnalyticsPage gains performance and interest-rate report types with metric selector; extractMetric utility added for multi-field report data
- **No breaking changes to existing reports**: Balance and net-change reports remain unchanged
