package ro.jf.funds.reporting.service.domain

data class ByGroup<T>(
    private val itemByBucket: Map<String, T>,
) : Iterable<Map.Entry<String, T>> {
    operator fun get(group: String): T? = itemByBucket[group]

    override fun iterator(): Iterator<Map.Entry<String, T>> {
        return itemByBucket.iterator()
    }
}
