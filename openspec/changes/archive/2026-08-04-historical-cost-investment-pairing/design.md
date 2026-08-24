## Context

The PerformanceService currently computes investment metrics using aggregated `UnitAmounts` — sums of amounts grouped by financial unit. This works for the instrument stream (totalInstrumentValue) and for ungrouped same-currency reports, but breaks in two cases:

1. **Grouping by FINANCIAL_UNIT**: the currency and instrument sides of an OPEN_POSITION are separate records with different `unit` values. When grouped by unit, the currency record (e.g., EUR -10) goes to group "EUR" while the instrument record (e.g., VT +15) goes to group "VT". Neither group can compute profit correctly.

2. **Cross-currency reporting**: the invested currency (e.g., EUR) is aggregated into a single sum and converted at the evaluation date. This conflates FX fluctuation with investment profit. Converting at each transaction's date gives the actual cost basis.

The repository already supports raw record retrieval (`getRecords`, `getRecordsBefore`) — the InterestRateService uses this pattern.

## Goals / Non-Goals

**Goals:**
- Investment amounts grouped by their paired instrument when grouping by FINANCIAL_UNIT
- Historical-cost conversion: each investment converted at its transaction date
- Correct per-instrument totalProfit in grouped-by-FINANCIAL_UNIT reports

**Non-Goals:**
- CLOSE_POSITION cost-basis attribution (FIFO/LIFO/average cost)
- Changes to the instrument stream (totalInstrumentValue) — stays aggregated, converted at evaluation date
- Changes to the InterestRateService
- Changes to API models or endpoints

## Decisions

### Decision: Fetch raw records for the investment stream, keep aggregated for instruments

The investment stream switches from `getUnitAmountsBefore`/`getBucketedUnitAmounts` (returns `GroupedUnitAmounts`) to `getRecordsBefore`/`getRecords` (returns `List<AnalyticsRecord>`). The instrument stream stays aggregated since it doesn't need per-transaction pairing.

**Alternative considered**: self-join SQL query to pair records at the DB level. Rejected because it adds query complexity and the pairing is straightforward in memory given the small record counts per user.

### Decision: Introduce InvestmentPosition domain type for paired records

```
InvestmentPosition(
    date: LocalDate,
    currencyUnit: Currency,
    currencyAmount: BigDecimal,
    instrumentUnit: Instrument,
    instrumentAmount: BigDecimal,
)
```

Raw OPEN_POSITION records are paired by `transactionId` to form positions. This type is internal to PerformanceService.

**Alternative considered**: working with raw record pairs without a dedicated type. Rejected for clarity — the pairing logic should produce a clear intermediate representation.

### Decision: Group investment positions by instrument unit

When grouping by FINANCIAL_UNIT, an InvestmentPosition with `instrumentUnit = VT` goes to group "VT" regardless of its `currencyUnit`. For other grouping criteria (FUND, ACCOUNT, CATEGORY), fall back to grouping by the currency record's matching field (fundId, accountId, category) since both sides of a transaction share these.

### Decision: Convert investment amounts at transaction date (historical cost)

Each position's currency amount is converted to the target currency using the rate at the position's date, not the bucket evaluation date. This requires one conversion call per unique (currency, date) pair across all accumulated positions.

To manage conversion call volume, batch all historical dates into a single ConversionsRequest per bucket where possible.

### Decision: Currency stream and currencyValue metric

The `currencyFilter` stream and `currencyValue` metric remain unchanged — they track the net currency position using aggregated UnitAmounts converted at evaluation date. When grouped by FINANCIAL_UNIT, currency records still group by their own unit (e.g., "EUR"), which is correct for that metric.

## Risks / Trade-offs

- **More conversion calls**: each historical position needs its own rate lookup instead of one aggregated conversion. For users with many transactions, this could increase latency. → Mitigation: batch conversion requests where possible; profile if it becomes an issue.
- **CLOSE_POSITION not handled**: the instrument stream still sums OPEN + CLOSE positions, but the investment stream only uses OPEN_POSITION. After a partial close, totalInvestment reflects the full cost but totalInstrumentValue reflects reduced holdings. → Mitigation: add TODO comments; address in a future change.
- **Breaking change in cross-currency ungrouped reports**: totalInvestment values will change for users whose investment currency differs from target currency. → Mitigation: acceptable since the new values are more correct.
