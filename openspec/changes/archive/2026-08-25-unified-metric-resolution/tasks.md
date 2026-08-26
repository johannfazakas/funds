# Tasks — Unified Metric Resolution

## 1. Metric model and registry

- [x] 1.1 Define the metric domain model in a new `metrics` package in analytics-service: `MetricDefinition` (name, output type, unit type, dependencies, resolver) and the sealed `MetricOutput` hierarchy (`BucketedScalars`, `RecordStore`, `CashFlows`, `Holdings`)
- [x] 1.2 Implement `MetricRegistry` with startup validation: unknown dependencies and cyclic graphs fail application boot with descriptive errors
- [x] 1.3 Unit-test registry validation (given a registry with a missing dependency, when the application validates it, then startup fails naming the metric and dependency; same for cycles)

## 2. Resolution engine

- [x] 2.1 Implement `MetricResolutionService`: dependency-closure expansion, topological ordering, once-per-request resolution into a request-scoped output map, returning only requested metrics
- [x] 2.2 Pass full bucketed dependency series and request parameters (interval, granularity, filters, groupBy, targetCurrency) to resolver functions
- [x] 2.3 Unit-test the engine with a synthetic metric graph (shared dependency resolved exactly once; transitive closure resolved in order; previous-bucket access works)

## 3. Metric resolvers

- [x] 3.1 Implement type-specific `RecordSet` leaf resolvers as the only repository access points: `positionRecords` (OPEN/CLOSE_POSITION query) and `transactionRecords` (types aggregated by balance/net-change), each returning opening + bucketed records per filter/groupBy
- [x] 3.2 Implement internal resolvers: `pairedPositions` (transactionId pairing of open/close position records into dated cash flows) and `instrumentHoldings`
- [x] 3.3 Implement currency metrics `balance` and `netChange` on top of `transactionRecords`
- [x] 3.4 Implement investment metrics `totalInvestment` and `currentInvestment` (historical-cost conversion at transaction date via `ConversionRateService`)
- [x] 3.5 Implement valuation metrics `totalInstrumentValue` (holdings priced and converted at bucket date) and `currencyValue`
- [x] 3.6 Implement derived scalar metrics `totalProfit` and `currentProfit` as compositions over dependency series
- [x] 3.7 Implement `totalInterestRate` and `currentInterestRate` reusing the existing bisection `InterestRateCalculator`, with `currentInterestRate` using the previous bucket's `totalInstrumentValue` (per group) as synthetic opening position
- [x] 3.8 Unit-test each resolver in isolation with the given-when-then naming pattern

## 4. API and SDK

- [x] 4.1 Add request/response TOs to analytics-api: metric request (metrics list, granularity, interval, filters, groupBy, targetCurrency) and response (buckets + per-metric series with unit type, grouped or ungrouped values)
- [x] 4.2 Add `POST /funds-api/analytics/v1/metrics` and `GET /funds-api/analytics/v1/metrics` routes in analytics-service, wired via Koin; reject unknown/internal metrics and empty metric lists with 400 naming the offenders
- [x] 4.3 Add SDK methods for both endpoints in analytics-sdk
- [x] 4.4 Integration-test the endpoints (TestContainers): single-metric, multi-metric, grouped, percentage series independent of target currency, 400 cases, discovery listing exactly the ten exposed metrics

## 5. Parity verification

- [x] 5.1 Build shared seed fixtures covering multi-currency investments, multiple funds/accounts/categories, open/close positions across buckets
- [x] 5.2 Parity tests ungrouped: for each of the four legacy endpoints, assert every metric value equals the legacy report field per bucket on identical inputs
- [x] 5.3 Parity tests grouped: assert per-group equality for each supported `groupBy`, including per-group `currentInterestRate` previous-bucket valuation
- [x] 5.4 Investigate and explicitly resolve any divergence found (fix new engine, or document a legacy bug and the intended behavior in the change)

## 6. Web client migration

- [x] 6.1 Add metric API functions to `analyticsApi.ts` (discovery + metric report), remove usage of `extractMetric`
- [x] 6.2 Replace the Report + Metric dropdowns in `AnalyticsPage.tsx` with a single metric dropdown populated from the discovery endpoint; keep a client-side label map keyed by metric name (raw name as fallback); format values by unit type
- [x] 6.3 Verify the page end-to-end against locally running services with imported data (balance, profit, and interest-rate metrics render correctly, grouped and ungrouped)

## 7. Wrap-up

- [x] 7.1 Run full builds and test suites for analytics modules and web-client
- [x] 7.2 Note the follow-up cleanup change (remove legacy report endpoints, services, and update legacy specs) in the project's management notes
