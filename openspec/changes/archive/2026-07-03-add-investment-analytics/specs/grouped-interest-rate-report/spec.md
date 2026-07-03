## ADDED Requirements

### Requirement: Grouped interest rate report by fund
The interest rate report SHALL return per-group metrics when the request specifies `groupBy: FUND`. Each group bucket SHALL contain an `InterestRateDataTO` with `totalInterestRate` and `currentInterestRate` computed from that fund's positions only. Group entries SHALL use the fund ID as `groupKey`.

#### Scenario: Interest rate report grouped by fund with two funds
- **WHEN** an interest rate report is requested with `groupBy: FUND` and the user has positions in Fund A and Fund B
- **THEN** each time bucket SHALL contain two group entries, one per fund, each with independently calculated interest rate metrics

#### Scenario: Interest rate report grouped by fund with no positions in early period
- **WHEN** an interest rate report is requested with `groupBy: FUND` and Fund B has no positions before the interval start
- **THEN** Fund B's group entry SHALL show zero interest rates in buckets before its first position

### Requirement: Grouped interest rate report by account
The interest rate report SHALL return per-group metrics when the request specifies `groupBy: ACCOUNT`. Group entries SHALL use the account ID as `groupKey`.

#### Scenario: Interest rate report grouped by account
- **WHEN** an interest rate report is requested with `groupBy: ACCOUNT`
- **THEN** each time bucket SHALL contain one group entry per account with interest rate metrics computed from that account's positions

### Requirement: Grouped interest rate report by financial unit
The interest rate report SHALL return per-group metrics when the request specifies `groupBy: FINANCIAL_UNIT`. Group entries SHALL use the unit value as `groupKey`.

#### Scenario: Interest rate report grouped by financial unit
- **WHEN** an interest rate report is requested with `groupBy: FINANCIAL_UNIT` and the user has positions in BTC and ETH
- **THEN** each time bucket SHALL contain separate group entries for BTC and ETH with interest rates reflecting each instrument's return

### Requirement: Grouped interest rate report by category
The interest rate report SHALL return per-group metrics when the request specifies `groupBy: CATEGORY`. Group entries SHALL use the category name as `groupKey` (nullable for uncategorized transactions).

#### Scenario: Interest rate report grouped by category
- **WHEN** an interest rate report is requested with `groupBy: CATEGORY` and transactions are categorized as "crypto" and "stocks"
- **THEN** each time bucket SHALL contain group entries for "crypto" and "stocks" with independently calculated interest rates

### Requirement: Per-group position accumulation for interest rate calculation
Each group SHALL independently accumulate positions for interest rate calculation. Raw records SHALL be partitioned by group key in-memory using a `groupByKey(groupBy)` helper. The interest rate calculator SHALL receive only positions belonging to the respective group, ensuring accurate per-group time-weighted returns.

#### Scenario: Independent position tracking per group
- **WHEN** a grouped interest rate report is requested with `groupBy: FUND` and Fund A has 3 positions and Fund B has 2 positions
- **THEN** Fund A's interest rate SHALL be calculated using only its 3 positions and Fund B's using only its 2 positions

### Requirement: Per-group valuation for current interest rate
The `currentInterestRate` for each group SHALL be computed by treating the previous bucket's valuation for that group as an aggregated position, consistent with the ungrouped calculation approach.

#### Scenario: Current interest rate uses previous group valuation
- **WHEN** a monthly grouped interest rate report is requested and a group had a valuation of 10000 at the end of January
- **THEN** the February current interest rate for that group SHALL use 10000 as the aggregated previous position
