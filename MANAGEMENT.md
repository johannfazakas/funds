# Roadmap

## Functional Tasks

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

### Categories instead of labels?

- [ ] group by label/category

### Better fund (and not only fund) matching

Maybe it could be done by account first. 
Maybe we could use domain accounts and labels.
Maybe matchers could work based on id.

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
