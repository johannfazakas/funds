package ro.jf.funds.analytics.service.service.series

import ro.jf.funds.analytics.service.service.AnalyticsConversionService
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.analytics.service.domain.ContextDimension
import ro.jf.funds.analytics.service.domain.DependencySlices
import ro.jf.funds.analytics.service.domain.GroupKey
import ro.jf.funds.analytics.service.domain.InvestmentPosition
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.domain.SeriesBucketResolver
import ro.jf.funds.analytics.service.domain.SeriesResolutionContext
import ro.jf.funds.analytics.service.domain.SeriesSlice
import ro.jf.funds.analytics.service.domain.UnitAmounts
import ro.jf.funds.analytics.service.domain.toGroupKey

class TotalInvestmentSeriesDefinition(
    private val conversions: AnalyticsConversionService,
) : SeriesDefinition<SeriesSlice.Scalars>(Series.TotalInvestment, ContextDimension.ALL, dependencies = listOf(POSITIONS)) {

    companion object Dependencies {
        private val POSITIONS = Series.PairedPositions
    }

    override fun createResolver(context: SeriesResolutionContext): SeriesBucketResolver<SeriesSlice.Scalars> =
        Resolver(context)

    private inner class Resolver(
        private val context: SeriesResolutionContext,
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
            positions.groupBy { it.toGroupKey(context.grouping) }.forEach { (groupKey, groupPositions) ->
                val converted = conversions.convertPositionsAtHistoricalCost(groupPositions, context.targetCurrency)
                runningInvestment[groupKey] = (runningInvestment[groupKey] ?: BigDecimal.ZERO) + converted
            }
        }
    }
}

class CurrentInvestmentSeriesDefinition(
    private val conversions: AnalyticsConversionService,
) : SeriesDefinition<SeriesSlice.Scalars>(Series.CurrentInvestment, ContextDimension.ALL, dependencies = listOf(POSITIONS)) {

    companion object Dependencies {
        private val POSITIONS = Series.PairedPositions
    }

    override fun createResolver(context: SeriesResolutionContext): SeriesBucketResolver<SeriesSlice.Scalars> =
        Resolver(context)

    private inner class Resolver(
        private val context: SeriesResolutionContext,
    ) : SeriesBucketResolver<SeriesSlice.Scalars> {
        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Scalars =
            SeriesSlice.Scalars.EMPTY

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Scalars {
            val values = inputs[POSITIONS].positions
                .groupBy { it.toGroupKey(context.grouping) }
                .mapValues { (_, groupPositions) ->
                    conversions.convertPositionsAtHistoricalCost(groupPositions, context.targetCurrency)
                }
            return SeriesSlice.Scalars(values)
        }
    }
}

abstract class CumulativeValueSeriesDefinition(
    metric: Series.Metric,
    private val dependency: Series.Internal<SeriesSlice.Amounts>,
    private val conversions: AnalyticsConversionService,
) : SeriesDefinition<SeriesSlice.Scalars>(metric, ContextDimension.ALL, dependencies = listOf(dependency)) {

    override fun createResolver(context: SeriesResolutionContext): SeriesBucketResolver<SeriesSlice.Scalars> =
        Resolver(context)

    private inner class Resolver(
        private val context: SeriesResolutionContext,
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
                values[groupKey] = conversions.convertAmounts(groupAmounts, context.targetCurrency, bucket.date)
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
    SeriesDefinition<SeriesSlice.Scalars>(Series.TotalProfit, ContextDimension.ALL, dependencies = listOf(VALUE, INVESTMENT)) {

    companion object Dependencies {
        private val VALUE = Series.TotalInstrumentValue
        private val INVESTMENT = Series.TotalInvestment
    }

    override fun createResolver(context: SeriesResolutionContext): SeriesBucketResolver<SeriesSlice.Scalars> =
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
    SeriesDefinition<SeriesSlice.Scalars>(Series.CurrentProfit, ContextDimension.ALL, dependencies = listOf(TOTAL_PROFIT)) {

    companion object Dependencies {
        private val TOTAL_PROFIT = Series.TotalProfit
    }

    override fun createResolver(context: SeriesResolutionContext): SeriesBucketResolver<SeriesSlice.Scalars> =
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
