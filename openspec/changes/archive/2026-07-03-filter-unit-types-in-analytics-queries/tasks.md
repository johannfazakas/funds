## 1. Filter Layer

- [x] 1.1 Add `unitTypes: List<UnitType> = emptyList()` field to `AnalyticsDbRecordFilter`
- [x] 1.2 Add `unitTypes` parameter to `AnalyticsInputRecordFilter.toDbFilter`
- [x] 1.3 Add SQL clause in `AnalyticsRecordRepository.applyFilter` to filter on `unit->>'type'` when `unitTypes` is non-empty

## 2. PerformanceService

- [x] 2.1 Pass `unitTypes = listOf(UnitType.CURRENCY)` for `investmentFilter`
- [x] 2.2 Pass `unitTypes = listOf(UnitType.INSTRUMENT)` for `instrumentFilter`
- [x] 2.3 Pass `unitTypes = listOf(UnitType.CURRENCY)` for `currencyFilter`
- [x] 2.4 Collapse `convertCurrencyUnits` and `convertInstrumentUnits` into a single `convertUnits` that converts all entries without type filtering
- [x] 2.5 Update `PerformanceServiceTest` filter expectations to include `unitTypes`

## 3. InterestRateService

- [x] 3.1 Replace the input-level unit filtering workaround (line 49) with `unitTypes = listOf(UnitType.INSTRUMENT)` in `toDbFilter`
- [x] 3.2 Update `InterestRateServiceTest` filter expectations if applicable

## 4. Verification

- [x] 4.1 Run unit tests: `./gradlew :service:analytics:analytics-service:test`
- [x] 4.2 Rebuild and restart service, verify endpoint response matches pre-refactor output
