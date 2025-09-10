package ro.jf.funds.reporting.service.domain

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Symbol

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

// TODO(Johann-UP) Check this out
data class ByCurrency<T>(
    private val itemByUnit: Map<Currency, T>,
) : Map<Currency, T> by itemByUnit

// TODO(Johann-UP) this should be removed, a new generic type should replace it.
data class BySymbol<T>(
    private val itemByUnit: Map<Symbol, T>,
) : Iterable<Map.Entry<Symbol, T>> {
    constructor(vararg items: Pair<Symbol, T>) : this(items.toMap())

    operator fun get(unit: Symbol): T? = itemByUnit[unit]

    // TODO(Johann-UP) might not be needed if I would inherit a Map
    fun asMap(): Map<Symbol, T> = itemByUnit

    fun plus(other: ByUnit<T>, plusFunction: (T, T) -> T): ByUnit<T> {
        return sequenceOf(this, other)
            .flatMap { it.asSequence() }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.reduce(plusFunction) }
            .let(::ByUnit)
    }

    override fun iterator(): Iterator<Map.Entry<Symbol, T>> {
        return itemByUnit.iterator()
    }

    fun <N> mapValues(transform: (Pair<Symbol, T>) -> N): BySymbol<N> {
        return BySymbol(itemByUnit.mapValues { (unit, initial) -> transform(unit to initial) })
    }
}
