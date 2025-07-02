package ro.jf.funds.reporting.service.domain

data class ByBucket<T>(
    val itemByBucket: Map<TimeBucket, T>,
) : Iterable<Map.Entry<TimeBucket, T>> {
    operator fun get(timeBucket: TimeBucket): T? = itemByBucket[timeBucket]

    override fun iterator(): Iterator<Map.Entry<TimeBucket, T>> {
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
