# metric-catalog (delta)

## ADDED Requirements

### Requirement: Metric calculation semantics
Metric values SHALL be computed with the following semantics, independent of any other capability:
- `BALANCE` accumulates all transaction amounts up to each bucket's end and converts the held unit amounts to the target currency at the bucket date; `NET_CHANGE` converts only the bucket's own amounts.
- `TOTAL_INVESTMENT` accumulates open-position cash flows converted at historical cost (each position's own transaction date); `CURRENT_INVESTMENT` is the bucket's own positions at historical cost.
- `TOTAL_INSTRUMENT_VALUE` and `CURRENCY_VALUE` accumulate instrument holdings and currency amounts respectively and convert them at each bucket's date.
- `TOTAL_PROFIT` is `TOTAL_INSTRUMENT_VALUE` minus `TOTAL_INVESTMENT` per bucket and group; `CURRENT_PROFIT` is the bucket-over-bucket delta of `TOTAL_PROFIT`.
- `TOTAL_INTEREST_RATE` and `CURRENT_INTEREST_RATE` are annualized money-weighted returns (XIRR) solved by bisection to the calculator's fixed precision over dated currency cash flows (investment positions become flows only once matured, i.e. dated on or before the bucket's valuation date) against the bucket's instrument valuation; `CURRENT_INTEREST_RATE` uses the previous bucket's valuation (seeded from the pre-interval holdings valuation at the interval start) as its opening position.
- Investment cash flows consider currency-typed records only; instrument records contribute quantities to holdings.

#### Scenario: Historical-cost investment conversion
- **WHEN** `TOTAL_INVESTMENT` is resolved in a target currency for positions opened on different dates
- **THEN** each position converts at the rate of its own transaction date, not the bucket date

#### Scenario: Interest rate via bisection
- **WHEN** `TOTAL_INTEREST_RATE` is resolved for a group with dated cash flows and a bucket valuation
- **THEN** the value is the bisection-solved annualized rate at the calculator's precision

#### Scenario: Current interest rate opening position
- **WHEN** `CURRENT_INTEREST_RATE` is resolved for bucket N
- **THEN** the calculation opens with bucket N−1's valuation (or the pre-interval holdings valuation for the first bucket)

## REMOVED Requirements

### Requirement: Parity with legacy report semantics
**Reason**: Defined metric values by reference to the legacy report capabilities (`ungrouped/grouped-performance-report`, `ungrouped/grouped-interest-rate-report`), which are removed in this change. Replaced by the self-contained "Metric calculation semantics" requirement above; the numeric expectations formerly enforced by the parity tests are frozen as fixed values in the metrics API tests.
**Migration**: None — values unchanged.
