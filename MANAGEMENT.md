# Roadmap

## Functional Tasks

### Integrate interest rate in performance report
- [ ] Integrate Performance report in Performance Report Resolver
- [ ] Integrate Performance report in Unit Performance Report Resolver

### Integrate interest rate in performance report forecast

### Add support for implicit transfers in funds format import
Fund matchers should be applicable to all import parsers
Actual case, implicit fund transfer for investment accounts interest transactions.

### Calculate interest rate when positions are closed

### UI, step 1
Anything, just have some AI and integrate the backend. Planning to use KMP would be great. 

### Integrate Pricing for ICBETNETF

### Decouple Report View from Report Data requests
The report view could be considered just a template for a dashboard. Its setting would be used to load the report data. 
But report data generation shouldn't depend on the report view.

### Predictions based on linear regression

## Non-Functional Tasks

### Remove unused account service

### Use sealed class on Records so Currency and Instrument record can be separated.
look for situations where a cast is used "as Instrument" or "as Currency". those could be replaced

### Integrate automatic linting
Integrate ktlint maybe. It could be applied automatically using Claude Code hooks.

### Improve Docker usage
- [ ] Have a bridge network
- [ ] Separate non-functional services from normal deployment

### Deployment option in kubernetes
Have it deployed with Helm Charts in kubernetes. At least locally in a minikube.

### Keep transactions and their records in the same table
This would make more sense, records should be directly attached and depend on transaction lifecycle.
GIN index could be used to filter by fundId. 
