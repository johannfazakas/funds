## MODIFIED Requirements

### Requirement: Performance report converts amounts to target currency
Investment amounts (totalInvestment, currentInvestment) SHALL be converted to the target currency using the conversion rate at each transaction's date (historical cost), not the bucket evaluation date. Instrument amounts (totalInstrumentValue) SHALL continue to use the conversion rate at the bucket evaluation date (current market value). Currency amounts (currencyValue) SHALL continue to use the conversion rate at the bucket evaluation date.

#### Scenario: Multi-currency portfolio with EUR target
- **WHEN** a performance report is requested with targetCurrency EUR and the user has USD and GBP investments
- **THEN** totalInvestment SHALL be the sum of each investment converted to EUR at its transaction date, and totalInstrumentValue SHALL be converted to EUR at the bucket evaluation date

#### Scenario: Same-currency investment
- **WHEN** a performance report is requested with targetCurrency EUR and all investments were made in EUR
- **THEN** totalInvestment SHALL equal the sum of EUR invested (historical-cost and present-value are identical for same-currency)

#### Scenario: Cross-currency investment with changing exchange rates
- **WHEN** the user invested 100 USD in January (rate: 1 USD = 0.90 EUR) and 100 USD in February (rate: 1 USD = 0.95 EUR) with targetCurrency EUR
- **THEN** totalInvestment in February SHALL be 185 EUR (90 + 95), not 190 EUR (200 × current rate of 0.95)

### Requirement: Performance report pairs investment records by transaction
The performance report SHALL pair currency and instrument records from the same OPEN_POSITION transaction using transactionId. Each paired investment position SHALL associate the currency amount spent with the instrument units acquired, enabling correct attribution of investment cost to specific instruments.

#### Scenario: Investment pairing via transactionId
- **WHEN** an OPEN_POSITION transaction has a currency record (EUR -1000) and an instrument record (VT +10) sharing the same transactionId
- **THEN** the performance report SHALL treat this as a single investment position: 1000 EUR invested in 10 VT
