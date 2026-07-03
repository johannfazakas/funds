## 1. Report Model and Repository Foundation

- [x] 1.1 Make AnalyticsReportTO generic with type parameter `<T>` on AnalyticsReportTO, AnalyticsBucketTO, and AnalyticsGroupBucketTO
- [x] 1.2 Rename CURRENCY to FINANCIAL_UNIT in GroupingCriteria
- [x] 1.3 Split AnalyticsRecordFilter into AnalyticsInputRecordFilter (user-driven: fundIds, units) and AnalyticsDbRecordFilter (database layer: fundIds, units, transactionTypes)
- [x] 1.4 Add transactionTypes filter to existing repository aggregate queries
- [x] 1.5 Add raw record queries (getRecordsBefore, getRecords) to AnalyticsRecordRepository

## 2. Domain Unification

- [x] 2.1 Add sealed GroupKey interface with Ungrouped, ByFund, ByAccount, ByFinancialUnit, ByCategory variants
- [x] 2.2 Unify domain value aggregates: GroupedUnitAmounts and BucketedUnitAmounts using GroupKey-keyed maps
- [x] 2.3 Unify repository aggregate methods into getUnitAmountsBefore and getBucketedUnitAmounts with optional groupBy parameter
- [x] 2.4 Update AnalyticsService balance and net-change reports to use unified code path with GroupKey

## 3. Domain and API Models

- [x] 3.1 Add InterestRateCalculator with time-weighted return via bisection
- [x] 3.2 Add PerformanceDataTO with totalInvestment, currentInvestment, totalProfit, currentProfit, totalInstrumentValue, currencyValue
- [x] 3.3 Add InterestRateDataTO with totalInterestRate, currentInterestRate

## 4. Performance Report

- [x] 4.1 Add PerformanceService with unified grouped/ungrouped performance report using per-group stateful bucket accumulation across investment, instrument, and currency filters
- [x] 4.2 Wire performance report endpoint into AnalyticsApiRouting
- [x] 4.3 Add unit tests for PerformanceService (ungrouped and grouped by fund)
- [x] 4.4 Add integration tests for performance report endpoint

## 5. Interest Rate Report

- [x] 5.1 Add InterestRateService with unified grouped/ungrouped interest rate report using per-group position-based calculation and in-memory record partitioning by group key
- [x] 5.2 Wire interest-rate report endpoint into AnalyticsApiRouting
- [x] 5.3 Add unit tests for InterestRateService (ungrouped and grouped by fund)
- [x] 5.4 Add integration tests for interest-rate report endpoint

## 6. Web Client

- [x] 6.1 Add performance and interest-rate report types to AnalyticsPage
- [x] 6.2 Add metric selector with extractMetric utility for multi-field report data
- [x] 6.3 Wire metric selector to only show for performance and interest-rate report types

## 7. Verification

- [x] 7.1 Run full analytics service test suite and verify all existing tests still pass
- [x] 7.2 Build and deploy locally, verify performance and interest-rate reports render correctly in the web client with group-by selector
