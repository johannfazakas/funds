## ADDED Requirements

### Requirement: Grouped performance report by fund
The performance report SHALL return per-group metrics when the request specifies `groupBy: FUND`. Each group bucket SHALL contain a `PerformanceDataTO` with `totalInvestment`, `currentInvestment`, `totalProfit`, `currentProfit`, `totalInstrumentValue`, and `currencyValue` computed from that fund's transactions only. Group entries SHALL use the fund ID as `groupKey`.

#### Scenario: Performance report grouped by fund with two funds
- **WHEN** a performance report is requested with `groupBy: FUND` and the user has transactions in Fund A and Fund B
- **THEN** each time bucket SHALL contain two group entries, one per fund, each with independently calculated performance metrics

#### Scenario: Performance report grouped by fund with no transactions in a fund for a period
- **WHEN** a performance report is requested with `groupBy: FUND` and Fund B has no transactions before the interval start
- **THEN** Fund B's group entry SHALL show zero values for `totalInvestment`, `totalProfit`, and `totalInstrumentValue` in buckets before its first transaction

### Requirement: Grouped performance report by account
The performance report SHALL return per-group metrics when the request specifies `groupBy: ACCOUNT`. Group entries SHALL use the account ID as `groupKey`.

#### Scenario: Performance report grouped by account
- **WHEN** a performance report is requested with `groupBy: ACCOUNT`
- **THEN** each time bucket SHALL contain one group entry per account with performance metrics computed from that account's transactions

### Requirement: Grouped performance report by financial unit
The performance report SHALL return per-group metrics when the request specifies `groupBy: FINANCIAL_UNIT`. Group entries SHALL use the unit value as `groupKey`.

#### Scenario: Performance report grouped by financial unit
- **WHEN** a performance report is requested with `groupBy: FINANCIAL_UNIT` and the user has positions in BTC and ETH
- **THEN** each time bucket SHALL contain separate group entries for BTC and ETH with performance metrics reflecting each instrument's contributions

### Requirement: Grouped performance report by category
The performance report SHALL return per-group metrics when the request specifies `groupBy: CATEGORY`. Group entries SHALL use the category name as `groupKey` (nullable for uncategorized transactions).

#### Scenario: Performance report grouped by category
- **WHEN** a performance report is requested with `groupBy: CATEGORY` and transactions are categorized as "crypto" and "stocks"
- **THEN** each time bucket SHALL contain group entries for "crypto" and "stocks" with independently calculated performance metrics

### Requirement: Grouped performance report accumulates state per group
Each group SHALL independently accumulate investment, instrument, and currency amounts across buckets using `Map<GroupKey, PerformanceState>`. The `totalProfit` for each group SHALL reflect that group's cumulative profit, and `currentProfit` SHALL reflect the change within the current bucket for that group.

#### Scenario: Cumulative profit tracking per group
- **WHEN** a monthly grouped performance report is requested with `groupBy: FUND` and Fund A has profit of 100 in January and 50 in February
- **THEN** Fund A's `totalProfit` SHALL be 100 in January and 150 in February, and `currentProfit` SHALL be 100 in January and 50 in February
