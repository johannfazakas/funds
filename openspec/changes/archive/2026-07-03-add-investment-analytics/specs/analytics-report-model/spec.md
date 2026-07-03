## MODIFIED Requirements

### Requirement: Generic analytics report model
AnalyticsReportTO, AnalyticsBucketTO, and AnalyticsGroupBucketTO SHALL use a type parameter `<T>` to support different report data types (BalanceDataTO, NetChangeDataTO, PerformanceDataTO, InterestRateDataTO).

#### Scenario: Report model carries performance data
- **WHEN** a performance report is requested
- **THEN** the response SHALL be an `AnalyticsReportTO<PerformanceDataTO>` with each group bucket containing a `PerformanceDataTO` value

#### Scenario: Report model carries interest rate data
- **WHEN** an interest rate report is requested
- **THEN** the response SHALL be an `AnalyticsReportTO<InterestRateDataTO>` with each group bucket containing an `InterestRateDataTO` value

#### Scenario: Existing balance reports unchanged
- **WHEN** a balance report is requested
- **THEN** the response SHALL remain an `AnalyticsReportTO<BalanceDataTO>` with no changes to the data structure

### Requirement: CURRENCY renamed to FINANCIAL_UNIT in GroupingCriteria
The `GroupingCriteria` enum value `CURRENCY` SHALL be renamed to `FINANCIAL_UNIT` to accurately reflect grouping by both currencies and instruments.

### Requirement: Sealed GroupKey interface with per-criteria variants
The domain layer SHALL use a sealed `GroupKey` interface with variants `Ungrouped`, `ByFund`, `ByAccount`, `ByFinancialUnit`, and `ByCategory`. Each variant SHALL expose an `apiValue: String?` property used when constructing API responses. `GroupKey.Ungrouped` SHALL use `apiValue = "UNGROUPED"`.

#### Scenario: Ungrouped report uses UNGROUPED group key
- **WHEN** a report is requested without `groupBy`
- **THEN** each bucket SHALL contain exactly one group entry with `groupKey: "UNGROUPED"`

#### Scenario: Grouped report uses criteria-specific group keys
- **WHEN** a report is requested with `groupBy: FUND`
- **THEN** each group entry SHALL have a `groupKey` containing the fund ID string

### Requirement: Split AnalyticsRecordFilter into input and database filters
AnalyticsInputRecordFilter SHALL contain user-driven filters (fundIds, units). AnalyticsDbRecordFilter SHALL extend this with transactionTypes for service-internal filtering. AnalyticsInputRecordFilter SHALL provide a `toDbFilter(transactionTypes)` convenience method.

#### Scenario: Service applies transaction type filter
- **WHEN** PerformanceService needs to filter by OPEN_POSITION
- **THEN** it SHALL call `inputFilter.toDbFilter(transactionTypes = listOf(TransactionType.OPEN_POSITION))` to construct the database filter

### Requirement: Transaction type filtering in repository
The analytics repository SHALL support filtering by `transactionTypes` on unit amount queries, allowing callers to distinguish investment transactions (OPEN_POSITION) from instrument transactions (OPEN_POSITION + CLOSE_POSITION).

#### Scenario: Filtering by OPEN_POSITION only
- **WHEN** a repository query specifies `transactionTypes = [OPEN_POSITION]`
- **THEN** only records with OPEN_POSITION type SHALL be included in the result

### Requirement: Unified repository methods with optional groupBy
The repository SHALL provide `getUnitAmountsBefore` and `getBucketedUnitAmounts` methods with an optional `groupBy: GroupingCriteria?` parameter. When null, the result SHALL use `GroupKey.Ungrouped`. When non-null, the result SHALL use per-criteria GroupKey variants.

#### Scenario: Ungrouped aggregate query
- **WHEN** `getUnitAmountsBefore` is called with `groupBy = null`
- **THEN** the result SHALL be a `GroupedUnitAmounts` with a single `GroupKey.Ungrouped` entry

#### Scenario: Grouped aggregate query
- **WHEN** `getUnitAmountsBefore` is called with `groupBy = FUND`
- **THEN** the result SHALL be a `GroupedUnitAmounts` with one `GroupKey.ByFund` entry per distinct fund

### Requirement: Raw record queries in repository
The analytics repository SHALL provide `getRecordsBefore` and `getRecords` methods returning individual `AnalyticsRecord` objects with date, amount, unit, fundId, accountId, and category fields, to support position-based calculations.

#### Scenario: Fetching records before a date
- **WHEN** `getRecordsBefore` is called with a date and transaction type filter
- **THEN** all matching individual records before that date SHALL be returned with their full field data
