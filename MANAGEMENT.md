# Roadmap

## Functional Tasks

### Improve import matching logic

- [ ] Fund matchers should be applicable to all import parsers. Actual case, implicit fund transfer for investment
  accounts interest transactions.
- [ ] Check common logic between Import Parser implementations
- [ ] Find a common strategy for transactionId generation
- [x] Allow multiple matching per matcher to simplify request definition
- [ ] verify that bt work taxes are correctly imported (ex. May CASS payment). Also check September 2021 minus.
- [ ] verify that distributed profit are correctly imported in investment accounts for currency interests

### Calculate interest rate when positions are closed

### Decouple Report View from Report Data requests

The report view could be considered just a template for a dashboard. Its setting would be used to load the report data.
But report data generation shouldn't depend on the report view.

### Add absolute changes in interest rate report

### Find a way to pinpoint unclassified records

## Infrastructure

### Improve Docker usage

- [ ] Have a bridge network
- [ ] Separate non-functional services from normal deployment

### Deployment option in kubernetes

Have it deployed with Helm Charts in kubernetes. At least locally in a minikube.

### Store CSV files in S3

## Non-Functional Tasks

### Use sealed class on Records so Currency and Instrument record can be separated.

look for situations where a cast is used "as Instrument" or "as Currency". those could be replaced

### Integrate automatic linting

Integrate ktlint maybe. It could be applied automatically using Claude Code hooks.

### Keep transactions and their records in the same table

This would make more sense, records should be directly attached and depend on transaction lifecycle.
GIN index could be used to filter by fundId.

### Investigate and understand the Exposed potential bug

Check AccountTransactionRepositoryTest

### Investigate & improve sealed classes' serialization

There seem to be another option to handle it with
SerializersModule: https://www.baeldung.com/kotlin/sealed-class-serialization
This would eliminate the need to use @OptIn(ExperimentalSerializationApi::class)

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
