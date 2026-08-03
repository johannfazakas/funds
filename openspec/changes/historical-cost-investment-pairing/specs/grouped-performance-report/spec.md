## MODIFIED Requirements

### Requirement: Grouped performance report by financial unit
The performance report SHALL return per-group metrics when the request specifies `groupBy: FINANCIAL_UNIT`. For instrument groups, the investment amounts (totalInvestment, currentInvestment) SHALL be attributed by pairing currency and instrument records from the same OPEN_POSITION transaction via transactionId — the investment goes to the group matching the paired instrument's unit. Group entries SHALL use the unit value as `groupKey`.

#### Scenario: Performance report grouped by financial unit
- **WHEN** a performance report is requested with `groupBy: FINANCIAL_UNIT` and the user has positions in BTC and ETH
- **THEN** each time bucket SHALL contain separate group entries for BTC and ETH with performance metrics reflecting each instrument's contributions, including the investment amounts attributed from paired currency records

#### Scenario: Investment attributed to paired instrument group
- **WHEN** the user bought 10 VT for 1000 EUR (one OPEN_POSITION transaction) and the report is grouped by FINANCIAL_UNIT
- **THEN** the "VT" group SHALL show totalInvestment of 1000 EUR (converted to target currency at transaction date) and the EUR investment SHALL NOT appear in a separate "EUR" group's totalInvestment

#### Scenario: Multiple instruments with separate investments
- **WHEN** the user bought 10 VT for 1000 EUR and 5 EUNL for 500 EUR in separate transactions, grouped by FINANCIAL_UNIT
- **THEN** the "VT" group SHALL show totalInvestment of 1000 EUR and the "EUNL" group SHALL show totalInvestment of 500 EUR, each converted at their respective transaction dates
