package ro.jf.funds.analytics.service.service.series

import ro.jf.funds.analytics.service.domain.MetricResolutionRequest
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.domain.SeriesBucketResolver
import ro.jf.funds.analytics.service.domain.SeriesSlice

abstract class SeriesDefinition<T : SeriesSlice>(
    val series: Series<T>,
    val dependencies: List<Series<*>> = emptyList(),
) {
    abstract fun createResolver(request: MetricResolutionRequest): SeriesBucketResolver<T>
}
