package ro.jf.funds.analytics.service.service.series

import ro.jf.funds.analytics.service.service.AnalyticsConversionService
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.analytics.service.domain.DependencySlices
import ro.jf.funds.analytics.service.domain.GroupKey
import ro.jf.funds.analytics.service.domain.InvestmentPosition
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.domain.SeriesBucketResolver
import ro.jf.funds.analytics.service.domain.MetricResolutionRequest
import ro.jf.funds.analytics.service.domain.SeriesSlice
import ro.jf.funds.analytics.service.domain.UnitAmounts
import ro.jf.funds.analytics.service.domain.toGroupKey

class TotalInvestmentSeriesDefinition(
    private val conversions: AnalyticsConversionService,
) : SeriesDefinition<SeriesSlice.Scalars>(Series.TotalInvestment, dependencies = listOf(POSITIONS)) {

    companion object Dependencies {
        private val POSITIONS = Series.PairedPositions
    }

    override fun createResolver(request: MetricResolutionRequest): SeriesBucketResolver<SeriesSlice.Scalars> =
        Resolver(request)

    private inner class Resolver(
        private val request: MetricResolutionRequest,
    ) : SeriesBucketResolver<SeriesSlice.Scalars> {
        private val runningInvestment = mutableMapOf<GroupKey, BigDecimal>()

        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Scalars {
            accumulate(previous[POSITIONS].positions)
            return SeriesSlice.Scalars.EMPTY
        }

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Scalars {
            accumulate(inputs[POSITIONS].positions)
            return SeriesSlice.Scalars(runningInvestment.toMap())
        }

        private suspend fun accumulate(positions: List<InvestmentPosition>) {
            positions.groupBy { it.toGroupKey(request.grouping) }.forEach { (groupKey, groupPositions) ->
                val converted = conversions.convertPositionsAtHistoricalCost(groupPositions, request.targetCurrency)
                runningInvestment[groupKey] = (runningInvestment[groupKey] ?: BigDecimal.ZERO) + converted
            }
        }
    }
}

class CurrentInvestmentSeriesDefinition(
    private val conversions: AnalyticsConversionService,
) : SeriesDefinition<SeriesSlice.Scalars>(Series.CurrentInvestment, dependencies = listOf(POSITIONS)) {

    companion object Dependencies {
        private val POSITIONS = Series.PairedPositions
    }

    override fun createResolver(request: MetricResolutionRequest): SeriesBucketResolver<SeriesSlice.Scalars> =
        Resolver(request)

    private inner class Resolver(
        private val request: MetricResolutionRequest,
    ) : SeriesBucketResolver<SeriesSlice.Scalars> {
        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Scalars =
            SeriesSlice.Scalars.EMPTY

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Scalars {
            val values = inputs[POSITIONS].positions
                .groupBy { it.toGroupKey(request.grouping) }
                .mapValues { (_, groupPositions) ->
                    conversions.convertPositionsAtHistoricalCost(groupPositions, request.targetCurrency)
                }
            return SeriesSlice.Scalars(values)
        }
    }
}

abstract class CumulativeValueSeriesDefinition(
    metric: Series.Metric,
    private val dependency: Series.Internal<SeriesSlice.Amounts>,
    private val conversions: AnalyticsConversionService,
) : SeriesDefinition<SeriesSlice.Scalars>(metric, dependencies = listOf(dependency)) {

    override fun createResolver(request: MetricResolutionRequest): SeriesBucketResolver<SeriesSlice.Scalars> =
        Resolver(request)

    private inner class Resolver(
        private val request: MetricResolutionRequest,
    ) : SeriesBucketResolver<SeriesSlice.Scalars> {
        private val amounts = mutableMapOf<GroupKey, UnitAmounts>()

        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Scalars {
            val previousAmounts = previous[dependency].amounts
            previousAmounts.groupKeys.forEach { groupKey -> amounts[groupKey] = previousAmounts[groupKey] }
            return SeriesSlice.Scalars.EMPTY
        }

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Scalars {
            val bucketAmounts = inputs[dependency].amounts
            for (groupKey in bucketAmounts.groupKeys) {
                amounts[groupKey] = (amounts[groupKey] ?: UnitAmounts.EMPTY) + bucketAmounts[groupKey]
            }
            val values = mutableMapOf<GroupKey, BigDecimal>()
            for ((groupKey, groupAmounts) in amounts) {
                values[groupKey] = conversions.convertAmounts(groupAmounts, request.targetCurrency, bucket.date)
            }
            return SeriesSlice.Scalars(values)
        }
    }
}

class TotalInstrumentValueSeriesDefinition(conversions: AnalyticsConversionService) :
    CumulativeValueSeriesDefinition(Series.TotalInstrumentValue, Series.InstrumentHoldings, conversions)

class CurrencyValueSeriesDefinition(conversions: AnalyticsConversionService) :
    CumulativeValueSeriesDefinition(Series.CurrencyValue, Series.CurrencyAmounts, conversions)

class TotalProfitSeriesDefinition :
    SeriesDefinition<SeriesSlice.Scalars>(Series.TotalProfit, dependencies = listOf(VALUE, INVESTMENT)) {

    companion object Dependencies {
        private val VALUE = Series.TotalInstrumentValue
        private val INVESTMENT = Series.TotalInvestment
    }

    override fun createResolver(request: MetricResolutionRequest): SeriesBucketResolver<SeriesSlice.Scalars> =
        Resolver()

    private inner class Resolver : SeriesBucketResolver<SeriesSlice.Scalars> {
        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Scalars =
            SeriesSlice.Scalars.EMPTY

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Scalars {
            val valueScalars = inputs[VALUE].values
            val investmentScalars = inputs[INVESTMENT].values
            val values = (valueScalars.keys + investmentScalars.keys).associateWith { groupKey ->
                (valueScalars[groupKey] ?: BigDecimal.ZERO) - (investmentScalars[groupKey] ?: BigDecimal.ZERO)
            }
            return SeriesSlice.Scalars(values)
        }
    }
}

class CurrentProfitSeriesDefinition :
    SeriesDefinition<SeriesSlice.Scalars>(Series.CurrentProfit, dependencies = listOf(TOTAL_PROFIT)) {

    companion object Dependencies {
        private val TOTAL_PROFIT = Series.TotalProfit
    }

    override fun createResolver(request: MetricResolutionRequest): SeriesBucketResolver<SeriesSlice.Scalars> =
        Resolver()

    private inner class Resolver : SeriesBucketResolver<SeriesSlice.Scalars> {
        private val previousProfit = mutableMapOf<GroupKey, BigDecimal>()

        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Scalars =
            SeriesSlice.Scalars.EMPTY

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Scalars {
            val values = inputs[TOTAL_PROFIT].values.mapValues { (groupKey, profit) ->
                val current = profit - (previousProfit[groupKey] ?: BigDecimal.ZERO)
                previousProfit[groupKey] = profit
                current
            }
            return SeriesSlice.Scalars(values)
        }
    }
}
