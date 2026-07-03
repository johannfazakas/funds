## Why

The analytics query filters only constrain by transaction type, not by unit type. Each data stream (investment, instruments, currency) fetches both CURRENCY and INSTRUMENT records, then discards the wrong type at conversion time. This makes the code confusing and error-prone — the `convertAll` bug we just fixed was a direct consequence of this design, where currency amounts leaked into `totalInstrumentValue`.

## What Changes

- Add a `unitTypes` filter to `AnalyticsDbRecordFilter` so queries can constrain by `UnitType` (CURRENCY, INSTRUMENT) at the DB level
- Update `AnalyticsInputRecordFilter.toDbFilter` to accept a `unitTypes` parameter
- Update `PerformanceService` to pass appropriate unit types when building filters: investment gets CURRENCY only, instruments gets INSTRUMENT only, currency gets CURRENCY only
- Update `InterestRateService` to use the new unit type filter instead of its current workaround (filtering input units by type on line 49)
- Simplify conversion functions — with data pre-filtered, `convertCurrencyUnits` and `convertInstrumentUnits` can be collapsed into a single `convertUnits` function
- Update `AnalyticsRecordRepository.applyFilter` to add the SQL clause filtering on the `type` field within the JSON `unit` column

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

(none — this is a pure internal refactoring with no spec-level behavior changes)

## Impact

- `AnalyticsDbRecordFilter` and `AnalyticsInputRecordFilter` — new `unitTypes` field
- `AnalyticsRecordRepository` — new SQL filter clause on JSON unit type
- `PerformanceService` — filter construction and conversion function simplification
- `InterestRateService` — adopt new filter, remove ad-hoc input-level filtering
- `PerformanceServiceTest` — update mock filter expectations to include unit types
- `InterestRateServiceTest` — update mock filter expectations if applicable
