# Roadmap

## Functional Tasks

### Calculate interest rate when positions are closed

### Decouple Report View from Report Data requests

The report view could be considered just a template for a dashboard. Its setting would be used to load the report data.
But report data generation shouldn't depend on the report view.

### Add absolute changes in interest rate report

### Find a way to pinpoint unclassified records

## Infrastructure

### Deployment option in kubernetes

Have it deployed with Helm Charts in kubernetes. At least locally in a minikube.

### Store CSV files in S3

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

### Keep transactions and their records in the same table

This would make more sense, records should be directly attached and depend on transaction lifecycle.
GIN index could be used to filter by fundId.

### Investigate and understand the Exposed potential bug

Check AccountTransactionRepositoryTest

### Improve Reporting Service code

- [ ] make error handling exhaustive
- [ ] check handling of conversion request errors
- [ ] previous records should be resolved only for the resolvers needed them

### Reevaluate monthly caching mechanism in instrument converter proxy
It caches info without storing them, leading to not persisted data.
It is applied partially on converters.
One idea could be to handle generating multiple requests at a higher level, maybe in Conversion Service.

### Caching capabilities for ConversionSDK

- [ ] SDK should take responsibility over the current code in reporting ConversionRateService
- [ ] Use the same component in import service
- [ ] Find a suitable in-memory caching solution
- [ ] Check error handling
- [ ] Look for same currency conversion, maybe it shouldn't trigger a request

### Use grpc for conversions
It would make sense to have a bidirectional streamed channel to resolve conversions.
