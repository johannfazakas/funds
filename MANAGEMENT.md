# Roadmap

## Functional Tasks

### Integrate transaction types in Reporting logic
- [ ] Improve Performance report filtering based on transaction types
- [ ] Integrate transaction types in other resolvers if helpful
- [ ] Adjust Performance report namings

### Integrate interest rate in performance report
- [ ] Integrate Performance report in Performance Report Resolver
- [ ] Integrate Performance report in Unit Performance Report Resolver

### Add support for implicit transfers in funds format import
Fund matchers should be applicable to all import parsers
Actual case, implicit fund transfer for investment accounts interest transactions.

### UI, step 1
Anything, just have some AI and integrate the backend. Planning to use KMP would be great. 

### Integrate Pricing for ICBETNETF

## Non-Functional Tasks

### Remove unused account service

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
