package ro.jf.funds.analytics.service.service.series

import ro.jf.funds.analytics.service.domain.ContextDimension
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.domain.SeriesBucketResolver
import ro.jf.funds.analytics.service.domain.SeriesResolutionContext
import ro.jf.funds.analytics.service.domain.SeriesSlice

abstract class SeriesDefinition<T : SeriesSlice>(
    val series: Series<T>,
    val contextSensitivity: Set<ContextDimension>,
    val dependencies: List<Series<*>> = emptyList(),
) {
    abstract fun createResolver(context: SeriesResolutionContext): SeriesBucketResolver<T>
}
