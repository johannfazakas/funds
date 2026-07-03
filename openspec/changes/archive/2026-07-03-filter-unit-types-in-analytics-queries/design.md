## Context

The analytics service queries records using three data streams that differ only by transaction type. Each stream fetches all unit types (CURRENCY and INSTRUMENT), then discards unwanted types at conversion time. This led to the `convertAll` bug where currency amounts leaked into `totalInstrumentValue`. The `InterestRateService` already works around this by pre-filtering input units (line 49), but this only filters user-requested units, not the underlying DB query.

The `unit` column in `analytics_record` is a JSON column storing `{"type":"currency","value":"EUR"}` or `{"type":"instrument","value":"EUNL"}`.

## Goals / Non-Goals

**Goals:**
- Add `unitTypes` filtering to `AnalyticsDbRecordFilter` so DB queries return only relevant unit types
- Each data stream fetches exactly the unit types it needs: investment → CURRENCY, instruments → INSTRUMENT, currency → CURRENCY
- Simplify conversion functions by collapsing `convertCurrencyUnits` and `convertInstrumentUnits` into a single `convertUnits` since data is pre-filtered
- Remove the ad-hoc workaround in `InterestRateService` (line 49 input-level unit filtering)

**Non-Goals:**
- Changing the semantics of `totalProfit` for currency groups (separate concern)
- Adding unit type filtering to the API request model
- Optimizing query performance beyond filtering correctness

## Decisions

### Add `unitTypes: List<UnitType>` to `AnalyticsDbRecordFilter`

Add a new field alongside the existing `units` (specific unit values) and `transactionTypes`. When non-empty, the SQL query filters records whose JSON `unit.type` matches any of the specified types.

**SQL filtering approach**: Use `jsonb` extraction on the `unit` column to filter by the `type` field: `unit->>'type' IN ('currency', 'instrument')`. This leverages PostgreSQL's native JSON support and is consistent with how the existing `units` filter uses `contains` on the same column.

**Alternative considered**: Filtering in Kotlin after the query returns. Rejected because it fetches unnecessary data and the bug stemmed from exactly this pattern of late filtering.

### Extend `toDbFilter` with a `unitTypes` parameter

`AnalyticsInputRecordFilter.toDbFilter` gains a `unitTypes: List<UnitType> = emptyList()` parameter, passed through to `AnalyticsDbRecordFilter`. Each service passes the appropriate unit types when constructing filters.

### Collapse conversion functions into a single `convertUnits`

With data pre-filtered, `convertCurrencyUnits` and `convertInstrumentUnits` are identical in logic — they only differed by the `UnitType` filter. Replace both with a single `convertUnits` that converts all entries it receives. This also removes the need for the `UnitType` import in `PerformanceService` since the filtering now happens at the query level.

### Update InterestRateService to use `unitTypes` filter

Replace the current workaround on line 49:
```kotlin
val instrumentFilter = filter.copy(units = filter.units.filter { it.type == UnitType.INSTRUMENT })
```
with the proper `unitTypes` parameter in `toDbFilter`. The `convert` function in `InterestRateService` already converts all entries (no type filtering), so it needs no changes.

## Risks / Trade-offs

- **JSON field extraction performance**: `unit->>'type'` on every query adds a small overhead. Mitigated by the fact that these queries already filter on `user_id`, `date_time`, and `transaction_type` which are more selective. A functional index on `(unit->>'type')` could be added later if needed.
- **Test updates required**: All mock expectations for `AnalyticsDbRecordFilter` need updating to include `unitTypes`. This is mechanical but touches many test assertions.
