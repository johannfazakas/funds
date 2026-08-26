package ro.jf.funds.analytics.service.service.series

import ro.jf.funds.analytics.service.service.AnalyticsConversionService
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.analytics.service.domain.*

class BalanceSeriesDefinition(
    private val conversions: AnalyticsConversionService,
) : SeriesDefinition<SeriesSlice.Scalars>(Series.Balance, dependencies = listOf(AMOUNTS)) {

    companion object Dependencies {
        private val AMOUNTS = Series.TransactionAmounts
    }

    override fun createResolver(request: MetricResolutionRequest): SeriesBucketResolver<SeriesSlice.Scalars> =
        Resolver(request)

    private inner class Resolver(
        private val request: MetricResolutionRequest,
    ) : SeriesBucketResolver<SeriesSlice.Scalars> {
        private val balances = mutableMapOf<GroupKey, UnitAmounts>()

        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Scalars {
            val previousAmounts = previous[AMOUNTS].amounts
            previousAmounts.groupKeys.forEach { groupKey -> balances[groupKey] = previousAmounts[groupKey] }
            return SeriesSlice.Scalars.EMPTY
        }

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Scalars {
            val values = mutableMapOf<GroupKey, BigDecimal>()
            for ((groupKey, balance) in balances) {
                values[groupKey] = conversions.convertAmounts(balance, request.targetCurrency, bucket.date)
            }
            val bucketAmounts = inputs[AMOUNTS].amounts
            for (groupKey in bucketAmounts.groupKeys) {
                balances[groupKey] = (balances[groupKey] ?: UnitAmounts.EMPTY) + bucketAmounts[groupKey]
            }
            return SeriesSlice.Scalars(values)
        }
    }
}
