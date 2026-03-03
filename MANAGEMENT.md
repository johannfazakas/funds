# Roadmap

## Functional Tasks

### Improved import

- [x] Store files
- [ ] Retry import
  - [ ] Re-upload
  - [ ] Revert previous import
  - [ ] Generate new import configuration from existing one
  - [ ] Import configuration matchers should be updateable only if not used
  - [ ] Re-import with new import configuration
- [ ] Use only kmp-compatible uuid
- [ ] UI display for imports menu
- [ ] Display and filter by configuration in import files list
- [ ] Use the same icon for delete operation
- [ ] Update import file and configuration on click in UI

### Colors for Funds, Accounts, Labels

### Calculate interest rate when positions are closed

### Decouple Report View from Report Data requests

The report view could be considered just a template for a dashboard. Its setting would be used to load the report data.
But report data generation shouldn't depend on the report view.

### Add absolute changes in interest rate report

### Find a way to pinpoint unclassified records

### UI Management of accounts, funds, and transactions

## Infrastructure

### Deployment option in kubernetes

Have it deployed with Helm Charts in kubernetes. At least locally in a minikube.

### Resource limits in docker

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

### Make the repo understandable

- [ ] expose relevant READMEs
- [ ] offer some demo setup
