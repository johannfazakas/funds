# Investment Analytics in Analytics Service

## Summary

Migrate the performance and interest rate investment reports from the reporting service into the analytics service, leveraging its event-driven materialized data store and existing grouping/filtering patterns.

## Motivation

The analytics service already materializes transaction data via Kafka and provides time-bucketed reports (balance, net-change) with grouping support. Moving investment analytics here:
- Eliminates the need to fetch transactions from fund-service on every report request
- Provides a consistent API surface for all analytics (same request/response format, same grouping model)
- Enables the reporting service to be deprecated over time

## Scope

**In scope:**
- Performance report (investment, profit, asset valuation)
- Interest rate report (annualized return rate)
- Replacing `CURRENCY` grouping criterion with `FINANCIAL_UNIT`
- Unifying response model with generic type parameter

**Out of scope:**
- Removing the reports from the reporting service (can coexist)
- New analytics beyond performance and interest rate (allocation, drawdown, etc.)
- Forecasting (reporting service retains this)

## Design

### API Changes

#### New Endpoints

```
POST /funds-api/analytics/v1/reports/performance
POST /funds-api/analytics/v1/reports/interest-rate
```

Both accept the existing `AnalyticsReportRequestTO` body (granularity, from, to, fundIds, units, targetCurrency, groupBy).

#### GroupingCriteria Change

Rename `CURRENCY` to `FINANCIAL_UNIT` in the `GroupingCriteria` enum. This groups by the `unit` field of `analytics_record`, which can be either a currency or an instrument. The repository logic is unchanged since `CURRENCY` already maps to the `unit` column.

#### Response Models

Unify all report responses using a generic type parameter on the existing model hierarchy (uses `com.ionspin.kotlin.bignum.decimal.BigDecimal` for KMP compatibility):

```kotlin
@Serializable
data class AnalyticsReportTO<T>(
    val granularity: TimeGranularity,
    val buckets: List<AnalyticsBucketTO<T>>,
)

@Serializable
data class AnalyticsBucketTO<T>(
    val dateTime: LocalDateTime,
    val groups: List<AnalyticsGroupBucketTO<T>>,
)

@Serializable
data class AnalyticsGroupBucketTO<T>(
    val groupKey: String? = null,
    val value: T,
)
```

Report-specific value types:

```kotlin
// Balance / Net-change: AnalyticsReportTO<BigDecimal> (existing behavior, just parameterized)

// Performance: AnalyticsReportTO<PerformanceDataTO>
@Serializable
data class PerformanceDataTO(
    val totalInvestment: BigDecimal,
    val currentInvestment: BigDecimal,
    val totalProfit: BigDecimal,
    val currentProfit: BigDecimal,
    val totalInstrumentValue: BigDecimal,
    val currencyValue: BigDecimal,
)

// Interest rate: AnalyticsReportTO<InterestRateDataTO>
@Serializable
data class InterestRateDataTO(
    val totalInterestRate: BigDecimal,
    val currentInterestRate: BigDecimal,
)
```

### Computation Logic

#### Performance Report

The performance report computes investment performance per time bucket by processing `analytics_record` entries:

1. **Currency value**: sum of ALL currency-unit records in the fund (all transaction types contribute — transfers, single records, exchanges, open/close positions)
2. **Investment amounts**: sum of currency-unit records from `OPEN_POSITION` transactions only (the cost of acquiring instruments)
3. **Instrument units held**: sum of instrument-unit records from `OPEN_POSITION` and `CLOSE_POSITION` transactions (net running unit count per instrument)
4. **Instrument valuation**: convert instrument units to target currency using ConversionSdk at each bucket's end date
5. **Profit**: (instrument value + currency value) - total investment

For cumulative behavior (total vs current), the logic mirrors the existing balance report: fetch previous-bucket state, then accumulate per bucket.

When grouped (e.g., by FINANCIAL_UNIT), each group tracks its own metrics independently. An OpenPosition transaction produces two records in `analytics_record` (one currency debit, one instrument credit). When grouped by FINANCIAL_UNIT, the currency record contributes to the currency group and the instrument record contributes to the instrument group.

#### Interest Rate Report

The interest rate report computes annualized return rates using an iterative bisection algorithm:

1. **Collect positions** from OpenPosition transactions (date + amount invested, converted to target currency)
2. **Calculate current valuation** by converting total instrument units to target currency at bucket end date
3. **Calculate interest rate** using the `InterestRateCalculator` algorithm (find the annualized rate that would grow all position amounts to the current valuation, accounting for time-weighted compounding)

**Total vs Current interest rate:**
- Total: uses all positions since inception, valued at bucket end
- Current: uses only current bucket's positions + previous bucket's aggregated valuation as a single position

When grouped, each group has its own positions and valuation, yielding independent interest rates.

### Repository Changes

#### Transaction Type Filtering

Add `transactionTypes: List<TransactionType>` to `AnalyticsRecordFilter`. This allows the existing aggregate query methods (`getBucketedUnitAmounts`, `getUnitAmountsBefore`, and their grouped variants) to be reused for performance computation:

- **Currency value**: query all records filtered to currency units (no transaction type filter) → `UnitAmounts` per currency → convert to target
- **Investment**: query with `transactionTypes=[OPEN_POSITION]`, filtered to currency units → `UnitAmounts` per currency → convert (negate = cost)
- **Instrument units**: query with `transactionTypes=[OPEN_POSITION, CLOSE_POSITION]`, filtered to instrument units → `UnitAmounts` per instrument → convert (= asset value)

This keeps the repository API consistent with balance/net-change reports.

#### Individual Records for Interest Rate

The interest rate calculator needs per-record detail (exact date + amount for each position). Add one new method:

```kotlin
suspend fun getRecords(
    userId: Uuid,
    interval: ReportInterval,
    filter: AnalyticsRecordFilter,
): List<AnalyticsRecord>

suspend fun getRecordsBefore(
    userId: Uuid,
    before: LocalDateTime,
    filter: AnalyticsRecordFilter,
): List<AnalyticsRecord>
```

These return individual `AnalyticsRecord` entries. The transaction type filter in `AnalyticsRecordFilter` restricts to `OPEN_POSITION` at the call site.

### InterestRateCalculator

The `InterestRateCalculator` class (currently in reporting-service) will be duplicated into the analytics service domain layer. The reporting service is on a deprecation path, so shared extraction adds unnecessary coupling.

The calculator depends only on `kotlinx-datetime` and `ch.obermuhlner:big-math` for exponentiation.

### Module Dependencies

The analytics-service already depends on:
- `fund-api` (for TransactionType, record models)
- `conversion-sdk` (for ConversionSdk)
- `platform-jvm` (shared utilities)

New dependency: `ch.obermuhlner:big-math` for the interest rate calculator.

## Web Client Changes

### Analytics Page Updates

The existing `AnalyticsPage.tsx` gains two new report type options in its report selector dropdown: "Performance" and "Interest Rate".

#### Report Type Selector

Current options: Balance, Net Change.
New options: Balance, Net Change, Performance, Interest Rate.

#### Metric Selector (new component)

When "Performance" or "Interest Rate" is selected, a secondary dropdown appears allowing the user to choose which metric to chart:

- **Performance metrics:** Total Investment, Current Investment, Total Profit, Current Profit, Total Instrument Value, Currency Value
- **Interest Rate metrics:** Total Interest Rate, Current Interest Rate

The selected metric is extracted from the response's `value` object and fed into the existing `ValueChart` (ungrouped) or `GroupedValueChart` (grouped) as a single `BigDecimal` per bucket — reusing the current chart components without modification.

#### TypeScript API Changes

Update `analyticsApi.ts`:
- Add `fetchPerformanceReport()` and `fetchInterestRateReport()` functions calling the new endpoints
- Update `GroupBy` type: rename `'CURRENCY'` to `'FINANCIAL_UNIT'`
- Add response types for the new reports:

```typescript
interface PerformanceData {
  totalInvestment: number;
  currentInvestment: number;
  totalProfit: number;
  currentProfit: number;
  totalInstrumentValue: number;
  currencyValue: number;
}

interface InterestRateData {
  totalInterestRate: number;
  currentInterestRate: number;
}

interface ReportResponse<T> {
  granularity: TimeGranularity;
  buckets: { dateTime: string; groups: { groupKey: string | null; value: T }[] }[];
}
```

The existing `ReportResponse` becomes `ReportResponse<number>` for balance/net-change, maintaining backward compatibility in shape.

#### Data Transformation

Add a utility that extracts a chosen metric from a typed response:

```typescript
function extractMetric<T>(report: ReportResponse<T>, metric: keyof T): ReportResponse<number>
```

This transforms a `ReportResponse<PerformanceData>` into a `ReportResponse<number>` by plucking the selected field, making it compatible with the existing `toSingleSeriesChartData()` / `toGroupedChartData()` utilities.

### No Changes Needed

- `ValueChart.tsx` / `GroupedValueChart.tsx` — unchanged (already render `number` values)
- Date/granularity/fund/unit/currency selectors — unchanged (same request model)
- Sidebar/navigation — unchanged (same `/analytics` route)

## Testing Strategy

- **Unit tests** for performance and interest rate computation logic with mocked ConversionSdk (following existing `AnalyticsServiceTest` patterns)
- **Integration tests** for the new endpoints using embedded PostgreSQL and Kafka (following existing `AnalyticsApiTest` patterns)
- Key test scenarios:
  - Single instrument, single currency
  - Multiple instruments, multiple currencies
  - OpenPosition + ClosePosition (partial and full)
  - Grouped vs ungrouped results
  - Mid-period intervals
  - Empty buckets (gap filling)
  - Transaction type filtering correctness

## Migration Notes

- The reporting service endpoints remain functional; this is additive
- Clients can migrate to analytics-service endpoints at their own pace
- The `CURRENCY` → `FINANCIAL_UNIT` rename in GroupingCriteria is a breaking change for existing analytics API consumers (balance/net-change reports) and should be communicated
- The generic type parameter on `AnalyticsReportTO<T>` is a breaking change to the existing response model (previously non-generic). Existing consumers using balance/net-change will need to update to `AnalyticsReportTO<BigDecimal>`
