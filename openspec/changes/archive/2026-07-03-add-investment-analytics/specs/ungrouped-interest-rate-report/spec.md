## ADDED Requirements

### Requirement: Interest rate report endpoint
The analytics service SHALL expose a `POST /funds-api/analytics/v1/reports/interest-rate` endpoint accepting an `AnalyticsReportRequestTO` with optional `groupBy` parameter and returning an `AnalyticsReportTO<InterestRateDataTO>`.

### Requirement: Interest rate report computes two metrics per bucket per group
Each group entry in an interest rate report SHALL contain an `InterestRateDataTO` with:
- `totalInterestRate`: cumulative annual interest rate computed from all historical positions via time-weighted return
- `currentInterestRate`: interest rate for the current bucket, treating the previous bucket's valuation as an aggregated position

#### Scenario: Interest rate with steady growth
- **WHEN** an interest rate report is requested and the portfolio grows steadily at 10% annual rate
- **THEN** totalInterestRate SHALL approximate 0.10 and currentInterestRate SHALL reflect the rate within each bucket period

#### Scenario: Interest rate in first bucket
- **WHEN** the first bucket of an interest rate report is computed
- **THEN** totalInterestRate SHALL be computed from all positions before and within the bucket, and currentInterestRate SHALL equal totalInterestRate (no previous valuation exists)

### Requirement: Ungrouped interest rate report uses UNGROUPED group key
When `groupBy` is null or not specified, the interest rate report SHALL return a single group entry per bucket with `groupKey: "UNGROUPED"`.

#### Scenario: Interest rate report without grouping
- **WHEN** an interest rate report is requested without `groupBy`
- **THEN** each time bucket SHALL contain exactly one group entry with `groupKey: "UNGROUPED"` and aggregated interest rate metrics

### Requirement: Time-weighted return calculation via bisection
The InterestRateCalculator SHALL compute annual interest rates using iterative bisection to find the rate that, when applied to dated positions (cash flows), produces the observed valuation. The calculation SHALL use 365-day year compounding with configurable precision (default 0.001%) and max iterations (default 100).

#### Scenario: Single position held for one year
- **WHEN** a position of 1000 is opened on January 1 and valued at 1100 on December 31
- **THEN** the calculated interest rate SHALL approximate 0.10 (10% annual return)

#### Scenario: Multiple positions at different dates
- **WHEN** 1000 is invested on January 1 and 500 is invested on July 1, with a total valuation of 1600 on December 31
- **THEN** the interest rate SHALL account for the timing of each cash flow, weighting the earlier investment more heavily

### Requirement: Interest rate report tracks currency positions only
The interest rate calculation SHALL use only currency-unit OPEN_POSITION records for position tracking. Instrument valuations SHALL be fetched separately and converted to target currency for the valuation amount.

#### Scenario: Currency and instrument positions
- **WHEN** the user has both EUR cash positions and BTC instrument positions
- **THEN** positions SHALL be built from EUR cash flow records, and valuation SHALL include BTC holdings converted to target currency at bucket date

### Requirement: Current interest rate uses previous bucket valuation
The currentInterestRate for each bucket SHALL be computed by treating the previous bucket's total valuation as a single aggregated position at the previous bucket's date, combined with any new positions within the current bucket.

#### Scenario: Current rate after large gain in previous period
- **WHEN** the portfolio was valued at 10000 at the end of January and grows to 10200 by end of February with no new investments
- **THEN** February's currentInterestRate SHALL reflect the annualized rate of the 200 gain on the 10000 base over one month
