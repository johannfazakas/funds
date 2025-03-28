package ro.jf.funds.reporting.service.domain

import ro.jf.funds.commons.model.FinancialUnit

data class ByUnit<T>(
    private val itemByUnit: Map<FinancialUnit, T>,
) : Iterable<Map.Entry<FinancialUnit, T>> {
    constructor(vararg items: Pair<FinancialUnit, T>) : this(items.toMap())

    operator fun get(unit: FinancialUnit): T? = itemByUnit[unit]

    fun plus(other: ByUnit<T>, plusFunction: (T, T) -> T): ByUnit<T> {
        return sequenceOf(this, other)
            .flatMap { it.asSequence() }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.reduce(plusFunction) }
            .let(::ByUnit)
    }

    override fun iterator(): Iterator<Map.Entry<FinancialUnit, T>> {
        return itemByUnit.iterator()
    }

    fun <N> mapValues(transform: (Pair<FinancialUnit, T>) -> N): ByUnit<N> {
        return ByUnit(itemByUnit.mapValues { (unit, initial) -> transform(unit to initial) })
    }
}

