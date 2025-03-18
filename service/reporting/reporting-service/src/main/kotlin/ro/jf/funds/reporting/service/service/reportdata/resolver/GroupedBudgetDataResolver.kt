package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.service.domain.Budget
import ro.jf.funds.reporting.service.domain.ByUnit

class GroupedBudgetDataResolver : ReportDataResolver<Map<String, ByUnit<Budget>>> {
    override fun resolve(input: ReportDataResolverInput): Map<DateInterval, Map<String, ByUnit<Budget>>>? {
        return null
    }
}
