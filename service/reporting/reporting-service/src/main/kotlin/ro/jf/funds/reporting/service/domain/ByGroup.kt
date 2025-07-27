package ro.jf.funds.reporting.service.domain

data class ByGroup<T>(
    private val itemByBucket: Map<String, T>,
) : Iterable<Map.Entry<String, T>> {
    constructor(vararg items: Pair<String, T>) : this(items.toMap())

    operator fun get(group: String): T? = itemByBucket[group]

    fun plus(other: ByGroup<T>, plusFunction: (T, T) -> T): ByGroup<T> {
        return sequenceOf(this, other)
            .flatMap { it.asSequence() }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.reduce(plusFunction) }
            .let(::ByGroup)
    }

    suspend fun <R> mapValues(transform: suspend (Pair<String, T>) -> R): ByGroup<R> {
        return ByGroup(itemByBucket.mapValues { (group, initial) -> transform(group to initial) })
    }

    override fun iterator(): Iterator<Map.Entry<String, T>> {
        return itemByBucket.iterator()
    }
}
