## Context

The analytics service supports balance and net-change reports with groupBy support. Performance and interest-rate analytics do not exist yet. The repository layer has aggregate query methods and the service layer uses a unified code path for grouped and ungrouped reports via a sealed GroupKey interface. The web client handles grouped chart rendering generically but has no metric selector for multi-field report data.

## Goals / Non-Goals

**Goals:**
- Establish generic report model infrastructure to support new report types
- Add performance reports with investment, profit, and instrument value metrics (unified grouped/ungrouped)
- Add interest-rate reports with total and current interest rates via time-weighted return calculation (unified grouped/ungrouped)
- Add web client support for performance and interest-rate reports with metric selection

**Non-Goals:**
- Adding new grouping criteria beyond the existing four (FINANCIAL_UNIT, ACCOUNT, FUND, CATEGORY)
- Adding forecasting to the analytics service
- Modifying the reporting service

## Decisions

### 1. Make AnalyticsReportTO generic with type parameter

The existing AnalyticsReportTO was hardcoded to a single data type. Introducing a type parameter `<T>` on AnalyticsReportTO, AnalyticsBucketTO, and AnalyticsGroupBucketTO allows the same report structure to carry PerformanceDataTO, InterestRateDataTO, or the existing balance/net-change data. This avoids duplicating the report envelope for each metric type.

**Alternative considered:** Separate report response types per metric. Rejected because it would duplicate the bucket/group structure and complicate the web client's generic chart rendering.

### 2. Rename CURRENCY to FINANCIAL_UNIT in GroupingCriteria

The CURRENCY grouping criterion groups by the unit field which contains both currencies and instruments (e.g., BTC, ETH). FINANCIAL_UNIT more accurately describes this behavior and avoids confusion when grouping investment positions by instrument.

### 3. Split AnalyticsRecordFilter into input and database filters

AnalyticsInputRecordFilter contains user-driven filters (fundIds, units). AnalyticsDbRecordFilter extends this with transactionTypes, which is a service-internal concern never driven by the user. AnalyticsInputRecordFilter provides a `toDbFilter(transactionTypes)` convenience method. This separation clarifies the API boundary: the routing layer constructs AnalyticsInputRecordFilter from the request, and each service decides which transactionTypes to apply.

**Alternative considered:** Single filter with optional transactionTypes. Rejected because it blurs the boundary between user input and service logic — callers could accidentally set transactionTypes from request parameters.

### 4. Sealed GroupKey interface with per-criteria variants

GroupKey is a sealed interface with variants Ungrouped, ByFund, ByAccount, ByFinancialUnit, and ByCategory. Each variant holds the typed group value and exposes an `apiValue: String?` for the API response. The repository constructs the right variant based on GroupingCriteria, and services iterate `Map<GroupKey, State>` generically without pattern matching. Ungrouped reports use `GroupKey.Ungrouped` with `apiValue = "UNGROUPED"`.

This replaces the previous `String?` map keys and eliminates the need for separate grouped/ungrouped domain types.

**Alternative considered:** Generic `GroupKey.Keyed(value: String)` without per-criteria variants. Rejected because per-criteria variants are more expressive and allow type-safe construction from repository results.

### 5. Unified repository methods with optional groupBy

The repository has two aggregate methods with optional `groupBy: GroupingCriteria? = null`:
- `getUnitAmountsBefore(userId, before, filter, groupBy?) → GroupedUnitAmounts`
- `getBucketedUnitAmounts(userId, interval, filter, groupBy?) → BucketedUnitAmounts`

When `groupBy` is null, the SQL groups by unit only and wraps the result with `GroupKey.Ungrouped`. When non-null, the SQL adds the group column to GROUP BY and constructs per-criteria GroupKey variants. The SQL if/else stays internal (different GROUP BY clauses can't be unified). Raw record methods (`getRecords`, `getRecordsBefore`) remain ungrouped — services partition records in-memory when needed.

### 6. Add transactionTypes filter and raw record queries to repository

Performance reports need to separate investment transactions (OPEN_POSITION) from instrument transactions (OPEN_POSITION + CLOSE_POSITION). Interest-rate reports need individual transaction records (not just aggregated amounts) to build position histories for the calculator. Two additions:
- `transactionTypes` filter on existing aggregate queries
- New `getRecordsBefore` and `getRecords` methods returning individual `AnalyticsRecord` objects

### 7. Time-weighted return calculation via bisection for interest rates

InterestRateCalculator uses iterative bisection to find the annual interest rate that, when applied to a series of dated positions (cash flows), produces the observed valuation. This is a standard internal rate of return approach adapted for time-weighted returns with 365-day year compounding. Configurable precision (0.001%) and max iterations (100).

**Alternative considered:** Simple return percentage (profit / investment). Rejected because it doesn't account for the timing of cash flows — early investments contribute more to returns than recent ones.

### 8. Unified service code path (no dispatcher pattern)

PerformanceService and InterestRateService each have a single method that accepts optional `groupBy`. The method always operates on `Map<GroupKey, State>` — for ungrouped, this map has a single `GroupKey.Ungrouped` entry. This eliminates the dispatcher pattern (`if (groupBy != null) grouped else ungrouped`) and the duplicated private methods that existed in AnalyticsService for balance/net-change reports.

**Alternative considered:** Separate methods for grouped and ungrouped (dispatcher pattern). Rejected because it caused 80-85% code duplication in the service layer.

### 9. PerformanceService with stateful bucket accumulation

PerformanceService computes 6 metrics per bucket per group: totalInvestment, currentInvestment, totalProfit, currentProfit, totalInstrumentValue, and currencyValue. It uses three separate repository queries with different filters (investments only, instruments, currency) and accumulates `Map<GroupKey, PerformanceState>` across buckets to compute cumulative totals and current-period deltas. All amounts are converted to the target currency at each bucket's date.

### 10. InterestRateService with position-based calculation

InterestRateService fetches raw transaction records and partitions them by group key using an in-memory `groupByKey(groupBy)` helper. It computes totalInterestRate from all historical positions per group and currentInterestRate by treating the previous bucket's valuation as an aggregated position. Only currency-unit positions (OPEN_POSITION type) are tracked; instrument valuations are fetched via grouped repository methods and converted to target currency.

### 11. Web client metric selector with extractMetric utility

Performance reports have 6 metrics and interest-rate reports have 2 metrics, but the chart renders a single value per data point. An `extractMetric<T>(report, metric)` utility extracts one field from the multi-field report data, producing a single-value report compatible with the existing chart components. The UI shows a metric dropdown only for report types with multiple fields.

## Risks / Trade-offs

**[Performance with many groups]** → Grouped performance/interest-rate reports multiply conversion API calls by the number of groups. Mitigated by existing conversion SDK caching (24h TTL). Monitor if group counts grow large.

**[Interest rate calculation per group]** → The bisection-based interest rate calculator runs per group per bucket. For N groups and M buckets, this is N*M calculations. Acceptable for typical portfolio sizes (< 20 groups) but could be slow for extreme cases. No mitigation needed now; optimization can be added later if needed.

**[Sparse groups in early buckets]** → Some groups may have no data in early time periods, leading to zero-value or undefined interest rates. Follow the existing pattern of filling with EMPTY values, consistent with how balance reports handle this.
