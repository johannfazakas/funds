package ro.jf.funds.analytics.service.service.series

import ro.jf.funds.analytics.service.service.AnalyticsConversionService
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toJavaBigDecimal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.analytics.service.domain.AnalyticsRecord
import ro.jf.funds.analytics.service.domain.ContextDimension
import ro.jf.funds.analytics.service.domain.DependencySlices
import ro.jf.funds.analytics.service.domain.GroupKey
import ro.jf.funds.analytics.service.domain.GroupedUnitAmounts
import ro.jf.funds.analytics.service.domain.InterestRateCalculationCommand
import ro.jf.funds.analytics.service.domain.InterestRateCalculator
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.domain.SeriesBucketResolver
import ro.jf.funds.analytics.service.domain.SeriesResolutionContext
import ro.jf.funds.analytics.service.domain.SeriesSlice
import ro.jf.funds.analytics.service.domain.toGroupKey
import ro.jf.funds.platform.api.model.UnitType

class TotalInterestRateSeriesDefinition(
    private val conversions: AnalyticsConversionService,
    private val interestRateCalculator: InterestRateCalculator,
) : SeriesDefinition<SeriesSlice.Scalars>(
    Series.TotalInterestRate,
    ContextDimension.ALL,
    dependencies = listOf(RECORDS, VALUATIONS),
) {

    companion object Dependencies {
        private val RECORDS = Series.OpenPositionRecords
        private val VALUATIONS = Series.TotalInstrumentValue
    }

    override fun createResolver(context: SeriesResolutionContext): SeriesBucketResolver<SeriesSlice.Scalars> =
        Resolver(context)

    private inner class Resolver(
        private val context: SeriesResolutionContext,
    ) : SeriesBucketResolver<SeriesSlice.Scalars> {
        private val accumulated = mutableMapOf<GroupKey, MutableList<InterestRateCalculationCommand.Position>>()
        private val pending = mutableMapOf<GroupKey, MutableList<InterestRateCalculationCommand.Position>>()
        private val groupKeys = mutableSetOf<GroupKey>()

        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Scalars {
            previous[RECORDS].records.toPositionsByGroup(conversions, context)
                .forEach { (groupKey, positions) ->
                    groupKeys.add(groupKey)
                    accumulated.getOrPut(groupKey) { mutableListOf() }.addAll(positions)
                }
            return SeriesSlice.Scalars.EMPTY
        }

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Scalars {
            inputs[RECORDS].records.toPositionsByGroup(conversions, context)
                .forEach { (groupKey, positions) ->
                    groupKeys.add(groupKey)
                    pending.getOrPut(groupKey) { mutableListOf() }.addAll(positions)
                }
            val bucketValuations = inputs[VALUATIONS].values
            groupKeys.addAll(bucketValuations.keys)

            val valuationDate = bucket.date
            val values = groupKeys.associateWith { groupKey ->
                val matured = pending[groupKey].takeMatured(valuationDate)
                accumulated.getOrPut(groupKey) { mutableListOf() }.addAll(matured)
                calculateRate(
                    interestRateCalculator,
                    accumulated.getValue(groupKey),
                    (bucketValuations[groupKey] ?: BigDecimal.ZERO).toJavaBigDecimal(),
                    valuationDate,
                )
            }
            return SeriesSlice.Scalars(values)
        }
    }
}

class CurrentInterestRateSeriesDefinition(
    private val conversions: AnalyticsConversionService,
    private val interestRateCalculator: InterestRateCalculator,
) : SeriesDefinition<SeriesSlice.Scalars>(
    Series.CurrentInterestRate,
    ContextDimension.ALL,
    dependencies = listOf(RECORDS, VALUATIONS, HOLDINGS),
) {

    companion object Dependencies {
        private val RECORDS = Series.OpenPositionRecords
        private val VALUATIONS = Series.TotalInstrumentValue
        private val HOLDINGS = Series.InstrumentHoldings
    }

    override fun createResolver(context: SeriesResolutionContext): SeriesBucketResolver<SeriesSlice.Scalars> =
        Resolver(context)

    private inner class Resolver(
        private val context: SeriesResolutionContext,
    ) : SeriesBucketResolver<SeriesSlice.Scalars> {
        private val pending = mutableMapOf<GroupKey, MutableList<InterestRateCalculationCommand.Position>>()
        private val previousValuations = mutableMapOf<GroupKey, InterestRateCalculationCommand.Position>()
        private val groupKeys = mutableSetOf<GroupKey>()
        private var previousHoldings: GroupedUnitAmounts = GroupedUnitAmounts.EMPTY

        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Scalars {
            previousHoldings = previous[HOLDINGS].amounts
            groupKeys.addAll(previous[RECORDS].records.map { it.toGroupKey(context.grouping) })
            return SeriesSlice.Scalars.EMPTY
        }

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Scalars {
            inputs[RECORDS].records.toPositionsByGroup(conversions, context)
                .forEach { (groupKey, positions) ->
                    groupKeys.add(groupKey)
                    pending.getOrPut(groupKey) { mutableListOf() }.addAll(positions)
                }
            val bucketValuations = inputs[VALUATIONS].values
            groupKeys.addAll(bucketValuations.keys)

            val valuationDate = bucket.date
            val values = mutableMapOf<GroupKey, BigDecimal>()
            for (groupKey in groupKeys) {
                val currentPositions = pending[groupKey].takeMatured(valuationDate)
                val valuation = (bucketValuations[groupKey] ?: BigDecimal.ZERO).toJavaBigDecimal()
                val previousValuation = previousValuations.getOrPut(groupKey) { previousHoldingsValuation(groupKey) }
                values[groupKey] = calculateRate(
                    interestRateCalculator,
                    currentPositions + previousValuation,
                    valuation,
                    valuationDate,
                )
                previousValuations[groupKey] = InterestRateCalculationCommand.Position(valuationDate, valuation)
            }
            return SeriesSlice.Scalars(values)
        }

        private suspend fun previousHoldingsValuation(groupKey: GroupKey): InterestRateCalculationCommand.Position {
            val fromDate = context.interval.from.date
            val valuation = conversions.convertAmounts(previousHoldings[groupKey], context.targetCurrency, fromDate)
            return InterestRateCalculationCommand.Position(fromDate, valuation.toJavaBigDecimal())
        }
    }
}

private suspend fun List<AnalyticsRecord>.toPositionsByGroup(
    conversions: AnalyticsConversionService,
    context: SeriesResolutionContext,
): Map<GroupKey, List<InterestRateCalculationCommand.Position>> =
    groupBy { it.toGroupKey(context.grouping) }
        .mapValues { (_, groupRecords) ->
            groupRecords
                .filter { it.unit.type == UnitType.CURRENCY }
                .map { record ->
                    val rate = conversions.rateOrOne(record.unit, context.targetCurrency, record.dateTime.date)
                    InterestRateCalculationCommand.Position(
                        date = record.dateTime.date,
                        amount = record.amount.negate().toJavaBigDecimal() * rate,
                    )
                }
        }

private fun MutableList<InterestRateCalculationCommand.Position>?.takeMatured(
    valuationDate: LocalDate,
): List<InterestRateCalculationCommand.Position> {
    if (this == null) return emptyList()
    val matured = filter { it.date <= valuationDate }
    removeAll { it.date <= valuationDate }
    return matured
}

private fun calculateRate(
    interestRateCalculator: InterestRateCalculator,
    positions: List<InterestRateCalculationCommand.Position>,
    valuation: java.math.BigDecimal,
    valuationDate: LocalDate,
): BigDecimal {
    val rate = if (positions.none { it.date < valuationDate } || valuation <= java.math.BigDecimal.ZERO) {
        java.math.BigDecimal.ZERO
    } else {
        interestRateCalculator.calculateInterestRate(
            InterestRateCalculationCommand(positions = positions, valuation = valuation, valuationDate = valuationDate)
        )
    }
    return BigDecimal.parseString(rate.toPlainString())
}
