package ro.jf.funds.analytics.service.service.series

import ro.jf.funds.analytics.service.service.AnalyticsConversionService
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.analytics.service.domain.*

class NetChangeSeriesDefinition(
    private val conversions: AnalyticsConversionService,
) : SeriesDefinition<SeriesSlice.Scalars>(Series.NetChange, ContextDimension.ALL, dependencies = listOf(AMOUNTS)) {

    companion object Dependencies {
        private val AMOUNTS = Series.TransactionAmounts
    }

    override fun createResolver(context: SeriesResolutionContext): SeriesBucketResolver<SeriesSlice.Scalars> =
        Resolver(context)

    private inner class Resolver(
        private val context: SeriesResolutionContext,
    ) : SeriesBucketResolver<SeriesSlice.Scalars> {
        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Scalars =
            SeriesSlice.Scalars.EMPTY

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Scalars {
            val bucketAmounts = inputs[AMOUNTS].amounts
            val values = bucketAmounts.groupKeys.associateWith { groupKey ->
                conversions.convertAmounts(bucketAmounts[groupKey], context.targetCurrency, bucket.date)
            }
            return SeriesSlice.Scalars(values)
        }
    }
}
