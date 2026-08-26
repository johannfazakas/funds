package ro.jf.funds.analytics.service.service.series

import kotlinx.datetime.LocalDateTime
import ro.jf.funds.analytics.service.domain.AnalyticsRecord
import ro.jf.funds.analytics.service.domain.BucketedGroupedUnitAmounts
import ro.jf.funds.analytics.service.domain.ContextDimension
import ro.jf.funds.analytics.service.domain.DependencySlices
import ro.jf.funds.analytics.service.domain.InvestmentPosition
import ro.jf.funds.analytics.service.domain.SeriesResolutionContext
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.domain.SeriesBucketResolver
import ro.jf.funds.analytics.service.domain.SeriesSlice
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.platform.api.model.UnitType

abstract class UnitAmountsLeafSeriesDefinition(
    series: Series.Internal<SeriesSlice.Amounts>,
    private val repository: AnalyticsRecordRepository,
    private val transactionTypes: List<TransactionType> = emptyList(),
    private val unitTypes: List<UnitType> = emptyList(),
) : SeriesDefinition<SeriesSlice.Amounts>(series, ContextDimension.ALL) {

    override fun createResolver(context: SeriesResolutionContext): SeriesBucketResolver<SeriesSlice.Amounts> =
        Resolver(context)

    private inner class Resolver(
        private val context: SeriesResolutionContext,
    ) : SeriesBucketResolver<SeriesSlice.Amounts> {
        private val dbFilter = context.filter.toDbFilter(transactionTypes, unitTypes)
        private var bucketed: BucketedGroupedUnitAmounts? = null

        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Amounts =
            SeriesSlice.Amounts(
                repository.getUnitAmountsBefore(context.userId, context.interval.from, dbFilter, context.grouping)
            )

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Amounts =
            SeriesSlice.Amounts(bucketed().getBucket(bucket))

        private suspend fun bucketed(): BucketedGroupedUnitAmounts =
            bucketed
                ?: repository.getBucketedUnitAmounts(context.userId, context.interval, dbFilter, context.grouping)
                    .also { bucketed = it }
    }
}

class TransactionAmountsSeriesDefinition(repository: AnalyticsRecordRepository) :
    UnitAmountsLeafSeriesDefinition(Series.TransactionAmounts, repository)

class InstrumentHoldingsSeriesDefinition(repository: AnalyticsRecordRepository) :
    UnitAmountsLeafSeriesDefinition(
        Series.InstrumentHoldings, repository,
        transactionTypes = listOf(TransactionType.OPEN_POSITION, TransactionType.CLOSE_POSITION),
        unitTypes = listOf(UnitType.INSTRUMENT),
    )

class CurrencyAmountsSeriesDefinition(repository: AnalyticsRecordRepository) :
    UnitAmountsLeafSeriesDefinition(Series.CurrencyAmounts, repository, unitTypes = listOf(UnitType.CURRENCY))

class OpenPositionRecordsSeriesDefinition(
    private val repository: AnalyticsRecordRepository,
) : SeriesDefinition<SeriesSlice.Records>(
    Series.OpenPositionRecords,
    contextSensitivity = setOf(ContextDimension.FILTER),
) {

    override fun createResolver(context: SeriesResolutionContext): SeriesBucketResolver<SeriesSlice.Records> =
        Resolver(context)

    private inner class Resolver(
        private val context: SeriesResolutionContext,
    ) : SeriesBucketResolver<SeriesSlice.Records> {
        private val dbFilter = context.filter.toDbFilter(transactionTypes = listOf(TransactionType.OPEN_POSITION))
        private var recordsByBucket: Map<LocalDateTime, List<AnalyticsRecord>>? = null

        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Records =
            SeriesSlice.Records(repository.getRecordsBefore(context.userId, context.interval.from, dbFilter))

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Records =
            SeriesSlice.Records(recordsByBucket()[bucket] ?: emptyList())

        private suspend fun recordsByBucket(): Map<LocalDateTime, List<AnalyticsRecord>> =
            recordsByBucket
                ?: repository.getRecords(context.userId, context.interval, dbFilter)
                    .groupBy { context.interval.bucketFor(it.dateTime) }
                    .also { recordsByBucket = it }
    }
}

class PairedPositionsSeriesDefinition : SeriesDefinition<SeriesSlice.Positions>(
    Series.PairedPositions,
    dependencies = listOf(RECORDS),
    contextSensitivity = setOf(ContextDimension.FILTER),
) {

    companion object Dependencies {
        private val RECORDS = Series.OpenPositionRecords
    }

    override fun createResolver(context: SeriesResolutionContext): SeriesBucketResolver<SeriesSlice.Positions> =
        Resolver()

    private inner class Resolver : SeriesBucketResolver<SeriesSlice.Positions> {
        override suspend fun resolvePrevious(previous: DependencySlices): SeriesSlice.Positions =
            SeriesSlice.Positions(previous[RECORDS].records.toInvestmentPositions())

        override suspend fun resolveBucket(bucket: LocalDateTime, inputs: DependencySlices): SeriesSlice.Positions =
            SeriesSlice.Positions(inputs[RECORDS].records.toInvestmentPositions())
    }
}

private fun List<AnalyticsRecord>.toInvestmentPositions(): List<InvestmentPosition> =
    groupBy { it.transactionId }
        .mapNotNull { (_, records) ->
            val currencyRecord = records.firstOrNull { it.unit.type == UnitType.CURRENCY } ?: return@mapNotNull null
            val instrumentRecord = records.firstOrNull { it.unit.type == UnitType.INSTRUMENT } ?: return@mapNotNull null
            InvestmentPosition(
                date = currencyRecord.dateTime.date,
                currencyUnit = currencyRecord.unit as Currency,
                currencyAmount = currencyRecord.amount,
                instrumentUnit = instrumentRecord.unit as Instrument,
                instrumentAmount = instrumentRecord.amount,
                fundId = currencyRecord.fundId,
                accountId = currencyRecord.accountId,
                category = currencyRecord.category?.value,
            )
        }
