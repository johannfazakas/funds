package ro.jf.funds.reporting.service.domain

import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Instrument

typealias ByBucket<V> = Map<TimeBucket, V>
typealias ByGroup<V> = Map<String, V>
typealias ByUnit<V> = Map<FinancialUnit, V>
typealias ByCurrency<V> = Map<Currency, V>
typealias ByInstrument<V> = Map<Instrument, V>

fun <K, V> Map<K, V>.merge(other: Map<K, V>, mergeFunction: (V, V) -> V): Map<K, V> = sequenceOf(this, other)
    .flatMap { it.asSequence() }
    .groupBy({ it.key }, { it.value })
    .mapValues { (_, values) -> values.reduce(mergeFunction) }
