package ro.jf.funds.reporting.service.domain

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Symbol

typealias ByBucket<V> = Map<TimeBucket, V>
typealias ByGroup<V> = Map<String, V>
typealias ByUnit<V> = Map<FinancialUnit, V>
typealias ByCurrency<V> = Map<Currency, V>
typealias BySymbol<V> = Map<Symbol, V>

fun <K, V> Map<K, V>.merge(other: Map<K, V>, mergeFunction: (V, V) -> V): Map<K, V> = sequenceOf(this, other)
    .flatMap { it.asSequence() }
    .groupBy({ it.key }, { it.value })
    .mapValues { (_, values) -> values.reduce(mergeFunction) }
