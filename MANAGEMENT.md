# Roadmap

## Functional Tasks

### Remove legacy analytics report endpoints

The unified metric resolution API (`POST/GET /funds-api/analytics/v1/metrics`) replaces the four
`/funds-api/analytics/v1/reports/*` endpoints. Once the web client migration is verified, remove the legacy
endpoints, `AnalyticsService`/`PerformanceService`/`InterestRateService`, and archive/update the legacy report
specs (`ungrouped/grouped-performance-report`, `ungrouped/grouped-interest-rate-report`).

### Investment Report

### Budgeted expense report

### Income report

### Colors for Funds, Accounts, Labels

### Calculate interest rate when positions are closed

### Decouple Report View from Report Data requests

The report view could be considered just a template for a dashboard. Its setting would be used to load the report data.
But report data generation shouldn't depend on the report view.

### Add absolute changes in interest rate report

### Find a way to pinpoint unclassified records

### Demo account

### Analytics Forecasting

### Partial transactions

A full transaction might be imported from 2 different import files. 
I.E. transfers.
If we would have this, it could make sense to remove the "skipped" accounts from import.

## Infrastructure

### Deployment option in kubernetes

Have it deployed with Helm Charts in kubernetes. At least locally in a minikube.

### Resource limits in docker

- [ ] Could we lower the resources' requirement?
- [ ] Set limits in docker.

### Add Loki for logging

- [ ] Control logging levels from config
- [ ] logback vs log4j2

## Non-Functional Tasks

### Improve report load time

- [ ] load data incrementally. how can I retrieve segments one by one?
- [ ] cache reporting data. how would I invalidate the cache?

#### Reports calculated ahead of time based on kafka events

Cons:
- would have to calculate data per Year, Month, Day, maybe also later Week? 
  - But this could be a dashboard feature to select the granularity.

#### Incremental data load, retrieve segments one by one

#### Cache reporting data

### Integrate automatic linting

Integrate ktlint maybe. It could be applied automatically using Claude Code hooks.

### Investigate and understand the Exposed potential bug

Check AccountTransactionRepositoryTest

### Reevaluate monthly caching mechanism in instrument converter proxy
It caches info without storing them, leading to not persisted data.
It is applied partially on converters.
One idea could be to handle generating multiple requests at a higher level, maybe in Conversion Service.

### Make the repo understandable

- [ ] expose relevant READMEs
- [ ] offer some demo setup

### Evaluate removing conversion classes from importer service, sdk might be enough

### Improve interest rate calculation algorithm

- XIRR (money-weighted return) — what you use now: the annualized discount rate that makes the NPV of all dated cash flows plus the final valuation equal zero. Sensitive to the timing and size of contributions, which is what you want for "how did my money do."
- Time-weighted return (TWR) — chains sub-period returns between each cash flow, neutralizing contribution timing. This is what fund managers report ("how did the strategy do"), and it would actually be cheap in your architecture since you already have per-bucket valuations.
- Modified Dietz — an approximation of the money-weighted return without iteration: gain divided by average invested capital, with cash flows day-weighted within the period. Cheaper than XIRR but drifts from it when flows are large or rates are high.
- Simple Dietz — the cruder version that assumes all flows happen mid-period.

Root-finding algorithms for XIRR

- Newton–Raphson — what Excel and most libraries use. Quadratic convergence (very fast), but needs the derivative of the NPV function and can diverge or oscillate with pathological cash flows (e.g., sign changes producing multiple roots, or a bad initial guess).
- Bisection — what you implemented. Only needs an initial bracket where NPV changes sign; converges linearly but guaranteed, and every iteration is trivially cheap. For a personal-finance workload the robustness-over-speed trade is the right one.
- Secant method — Newton without the analytic derivative (approximates it from the last two points). Faster than bisection, but shares Newton's non-convergence risk.
- Brent's method — the "best of both" hybrid: combines bisection's guaranteed bracketing with inverse quadratic interpolation's speed. It's what SciPy's brentq uses and would be the natural upgrade if bisection ever felt slow — though at your data sizes it won't.

### Use non-blocking DB

### Use non-blocking Kafka

### Reduce memory consumption by using GraalVM

## Bugs & investigations

### Huge jump in Total interest rate around 2022

