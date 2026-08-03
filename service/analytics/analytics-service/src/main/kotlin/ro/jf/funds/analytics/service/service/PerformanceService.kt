package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.*
import ro.jf.funds.analytics.service.domain.*
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.platform.api.model.UnitType

private val log = logger { }

class PerformanceService(
    private val analyticsRecordRepository: AnalyticsRecordRepository,
    private val conversionSdk: ConversionSdk,
) {
    data class InvestmentPosition(
        val date: LocalDate,
        val currencyUnit: Currency,
        val currencyAmount: BigDecimal,
        val instrumentUnit: Instrument,
        val instrumentAmount: BigDecimal,
        val fundId: Uuid,
        val accountId: Uuid,
        val category: String?,
    )

    private data class PerformanceState(
        val positions: List<InvestmentPosition>,
        val instruments: UnitAmounts,
        val currency: UnitAmounts,
        val previousTotalProfit: BigDecimal,
    )

    suspend fun getPerformanceReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsInputRecordFilter = AnalyticsInputRecordFilter(),
        targetCurrency: Currency,
        groupBy: GroupingCriteria? = null,
    ): AnalyticsReportTO<PerformanceDataTO> {
        log.info { "Generating performance report for user $userId, interval=$interval, targetCurrency=$targetCurrency, groupBy=$groupBy" }

        val investmentFilter = filter.toDbFilter(
            transactionTypes = listOf(TransactionType.OPEN_POSITION),
        )
        val instrumentFilter = filter.toDbFilter(
            transactionTypes = listOf(TransactionType.OPEN_POSITION, TransactionType.CLOSE_POSITION),
            unitTypes = listOf(UnitType.INSTRUMENT),
        )
        val currencyFilter = filter.toDbFilter(
            unitTypes = listOf(UnitType.CURRENCY),
        )

        val prevInvestmentRecords = analyticsRecordRepository.getRecordsBefore(userId, interval.from, investmentFilter)
        val prevPositions = prevInvestmentRecords.toInvestmentPositions()
        val prevPositionsByGroup = prevPositions.groupByKey(groupBy)

        val prevInstruments = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, instrumentFilter, groupBy)
        val prevCurrency = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, currencyFilter, groupBy)

        val bucketInvestmentRecords = analyticsRecordRepository.getRecords(userId, interval, investmentFilter)
        val bucketPositions = bucketInvestmentRecords.toInvestmentPositions()
        val bucketedPositions = bucketPositions.bucketByInterval(interval)

        val bucketedInstruments = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, instrumentFilter, groupBy)
        val bucketedCurrency = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, currencyFilter, groupBy)

        val allGroupKeys = (prevPositionsByGroup.keys + prevInstruments.groupKeys + prevCurrency.groupKeys +
            bucketPositions.map { it.toGroupKey(groupBy) }.toSet() +
            bucketedInstruments.groupKeys + bucketedCurrency.groupKeys)
            .ifEmpty { setOf(GroupKey.Ungrouped) }

        val initialStates = allGroupKeys.associateWith { groupKey ->
            PerformanceState(
                positions = prevPositionsByGroup[groupKey] ?: emptyList(),
                instruments = prevInstruments[groupKey],
                currency = prevCurrency[groupKey],
                previousTotalProfit = BigDecimal.ZERO,
            )
        }.toMutableMap()

        val buckets = interval.generateBucketedData(initialStates) { dateTime: LocalDateTime, statesByGroup: MutableMap<GroupKey, PerformanceState> ->
            val instrumentsBucket = bucketedInstruments.getBucket(dateTime)
            val currencyBucket = bucketedCurrency.getBucket(dateTime)
            val currentBucketPositions = bucketedPositions[dateTime] ?: emptyList()

            val groupBuckets = statesByGroup.map { (groupKey, state) ->
                val currentPositionsForGroup = currentBucketPositions
                    .filter { it.toGroupKey(groupBy) == groupKey }

                val allPositions = state.positions + currentPositionsForGroup
                val updatedState = PerformanceState(
                    positions = allPositions,
                    instruments = state.instruments + instrumentsBucket[groupKey],
                    currency = state.currency + currencyBucket[groupKey],
                    previousTotalProfit = BigDecimal.ZERO,
                )

                // TODO: CLOSE_POSITION cost-basis — when closing positions, totalInvestment should be reduced proportionally
                val totalInvestment = convertPositionsToInvestment(allPositions, targetCurrency)
                val currentInvestment = convertPositionsToInvestment(currentPositionsForGroup, targetCurrency)
                val totalInstrumentValue = convertUnits(updatedState.instruments, targetCurrency, dateTime.date)
                val currencyValue = convertUnits(updatedState.currency, targetCurrency, dateTime.date)
                val totalProfit = totalInstrumentValue - totalInvestment
                val currentProfit = totalProfit - state.previousTotalProfit

                statesByGroup[groupKey] = updatedState.copy(previousTotalProfit = totalProfit)

                AnalyticsGroupBucketTO(
                    groupKey = groupKey.apiValue,
                    value = PerformanceDataTO(
                        totalInvestment = totalInvestment,
                        currentInvestment = currentInvestment,
                        totalProfit = totalProfit,
                        currentProfit = currentProfit,
                        totalInstrumentValue = totalInstrumentValue,
                        currencyValue = currencyValue,
                    ),
                )
            }
            AnalyticsBucketTO(dateTime, groupBuckets) to statesByGroup
        }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    private suspend fun convertPositionsToInvestment(
        positions: List<InvestmentPosition>, targetCurrency: Currency,
    ): BigDecimal {
        if (positions.isEmpty()) return BigDecimal.ZERO
        val conversionKeys = positions.map { Triple(it.currencyUnit, targetCurrency, it.date) }.distinct()
        val request = ConversionsRequest(conversionKeys.map { (source, target, date) ->
            ConversionRequest(source, target, date)
        })
        val rates = conversionSdk.convert(request)
        return positions.fold(BigDecimal.ZERO) { acc, position ->
            val rate = rates.getRate(position.currencyUnit, targetCurrency, position.date)
            if (rate == null) {
                log.warn { "Conversion rate not found for ${position.currencyUnit} -> $targetCurrency on ${position.date}, treating as zero" }
                return@fold acc
            }
            acc + position.currencyAmount.negate() * rate
        }
    }

    private suspend fun convertUnits(
        amounts: UnitAmounts, targetCurrency: Currency, date: LocalDate,
    ): BigDecimal {
        if (amounts.units.isEmpty()) return BigDecimal.ZERO
        val request = ConversionsRequest(amounts.units.map { ConversionRequest(it, targetCurrency, date) })
        val rates = conversionSdk.convert(request)
        return amounts.entries.fold(BigDecimal.ZERO) { acc, (unit, amount) ->
            val rate = rates.getRate(unit, targetCurrency, date)
            if (rate == null) {
                log.warn { "Conversion rate not found for $unit -> $targetCurrency on $date, treating as zero" }
                return@fold acc
            }
            acc + amount * rate
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

    private fun InvestmentPosition.toGroupKey(groupBy: GroupingCriteria?): GroupKey = when (groupBy) {
        GroupingCriteria.FINANCIAL_UNIT -> GroupKey.ByFinancialUnit(instrumentUnit.value)
        GroupingCriteria.FUND -> GroupKey.ByFund(fundId.toString())
        GroupingCriteria.ACCOUNT -> GroupKey.ByAccount(accountId.toString())
        GroupingCriteria.CATEGORY -> GroupKey.ByCategory(category)
        null -> GroupKey.Ungrouped
    }

    private fun List<InvestmentPosition>.groupByKey(groupBy: GroupingCriteria?): Map<GroupKey, List<InvestmentPosition>> =
        groupBy { it.toGroupKey(groupBy) }

    private fun List<InvestmentPosition>.bucketByInterval(interval: ReportInterval): Map<LocalDateTime, List<InvestmentPosition>> {
        val fromTruncated = interval.truncate(interval.from)
        return groupBy { pos ->
            val posDateTime = LocalDateTime(pos.date, LocalTime(0, 0))
            val truncated = interval.truncate(posDateTime)
            if (truncated == fromTruncated) interval.from else truncated
        }
    }
}
