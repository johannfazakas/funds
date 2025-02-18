package ro.jf.funds.reporting.service.domain

import ro.jf.funds.commons.model.FinancialUnit

data class ByUnit<T>(
    private val itemByUnit: Map<FinancialUnit, T>,
) : Iterable<Map.Entry<FinancialUnit, T>> {
    operator fun get(unit: FinancialUnit): T? = itemByUnit[unit]

    override fun iterator(): Iterator<Map.Entry<FinancialUnit, T>> {
        return itemByUnit.iterator()
    }

    fun <N> mapValues(transform: (FinancialUnit, T) -> N): ByUnit<N> {
        return ByUnit(itemByUnit.mapValues { (unit, initial) -> transform(unit, initial) })
    }
}

