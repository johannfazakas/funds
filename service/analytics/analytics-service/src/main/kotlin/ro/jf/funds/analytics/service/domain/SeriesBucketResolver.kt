package ro.jf.funds.analytics.service.domain

import kotlinx.datetime.LocalDateTime

class DependencySlices(
    private val bySeries: Map<Series<*>, SeriesSlice>,
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T : SeriesSlice> get(series: Series<T>): T =
        (bySeries[series] ?: throw IllegalStateException("Dependency '$series' slice was not provided")) as T

    companion object {
        val EMPTY = DependencySlices(emptyMap())
    }
}

interface SeriesBucketResolver<out T : SeriesSlice> {
    suspend fun resolvePrevious(previous: DependencySlices): T

    suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): T
}
