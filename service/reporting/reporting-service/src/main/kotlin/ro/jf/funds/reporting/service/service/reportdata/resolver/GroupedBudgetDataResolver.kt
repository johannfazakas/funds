package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.service.domain.Budget
import ro.jf.funds.reporting.service.domain.ByBucket
import ro.jf.funds.reporting.service.domain.ByGroup
import ro.jf.funds.reporting.service.domain.ByUnit
import java.math.BigDecimal

class GroupedBudgetDataResolver : ReportDataResolver<ByGroup<ByUnit<Budget>>> {
    override fun resolve(input: ReportDataResolverInput): ByBucket<ByGroup<ByUnit<Budget>>>? {
        val previousLeftBudgets = calculatePreviousLeftBudgets()
        return null
    }

    private fun calculatePreviousLeftBudgets(): ByGroup<ByUnit<BigDecimal>> {
        return ByGroup(emptyMap())
    }
}
