package ro.jf.funds.reporting.service.domain

import ro.jf.funds.reporting.api.model.DateInterval

data class ByBucket<T>(
    private val itemByBucket: Map<DateInterval, T>,
) : Iterable<Map.Entry<DateInterval, T>> {
    operator fun get(dateInterval: DateInterval): T? = itemByBucket[dateInterval]

    override fun iterator(): Iterator<Map.Entry<DateInterval, T>> {
        return itemByBucket.iterator()
    }

    operator fun plus(other: ByBucket<T>): ByBucket<T> {
        return sequenceOf(this, other)
            .flatMap { it.asSequence() }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.reduce { acc, t -> error("Merge situation occurred while adding ByBucket instances.") } }
            .let(::ByBucket)
    }
}
