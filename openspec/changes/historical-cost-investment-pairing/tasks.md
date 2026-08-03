## 1. Domain Model

- [x] 1.1 Create `InvestmentPosition` data class in PerformanceService (date, currencyUnit, currencyAmount, instrumentUnit, instrumentAmount)
- [x] 1.2 Create helper function to pair raw `AnalyticsRecord` lists into `List<InvestmentPosition>` by transactionId

## 2. PerformanceService Refactor

- [x] 2.1 Replace aggregated investment queries (`getUnitAmountsBefore`/`getBucketedUnitAmounts` with investmentFilter) with raw record queries (`getRecordsBefore`/`getRecords` with OPEN_POSITION filter)
- [x] 2.2 Pair raw investment records into `InvestmentPosition` lists and group by instrument unit when grouping by FINANCIAL_UNIT (fall back to fundId/accountId/category for other grouping criteria)
- [x] 2.3 Compute `totalInvestment` by converting each position's currency amount at its transaction date (historical cost)
- [x] 2.4 Compute `currentInvestment` from positions in the current bucket only, converted at their transaction dates
- [x] 2.5 Add TODO comments for CLOSE_POSITION cost-basis handling where relevant

## 3. Tests

- [x] 3.1 Update existing ungrouped tests to use raw record mocks for the investment stream instead of aggregated UnitAmounts mocks
- [x] 3.2 Add test: cross-currency investment converts at transaction date, not evaluation date
- [x] 3.3 Add test: grouped by FINANCIAL_UNIT — investment attributed to paired instrument group, not currency group
- [x] 3.4 Add test: grouped by FINANCIAL_UNIT — multiple instruments get correct per-instrument totalInvestment

## 4. Verification

- [x] 4.1 Run unit tests: `./gradlew :service:analytics:analytics-service:test`
- [x] 4.2 Rebuild and restart service, verify endpoint responds without errors
