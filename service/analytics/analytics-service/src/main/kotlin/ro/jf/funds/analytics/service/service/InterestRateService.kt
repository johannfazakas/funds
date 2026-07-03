package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toJavaBigDecimal
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.*
import ro.jf.funds.analytics.service.domain.AnalyticsInputRecordFilter
import ro.jf.funds.analytics.service.domain.AnalyticsRecord
import ro.jf.funds.analytics.service.domain.GroupKey
import ro.jf.funds.analytics.service.domain.InterestRateCalculationCommand
import ro.jf.funds.analytics.service.domain.InterestRateCalculator
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.UnitAmounts
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.UnitType

private val log = logger { }

class InterestRateService(
    private val analyticsRecordRepository: AnalyticsRecordRepository,
    private val conversionSdk: ConversionSdk,
    private val interestRateCalculator: InterestRateCalculator,
) {
    private data class InterestRateState(
        val allPositions: List<InterestRateCalculationCommand.Position>,
        val instrumentUnits: UnitAmounts,
        val previousValuation: BigDecimal,
        val previousValuationDate: LocalDate,
    )

    suspend fun getInterestRateReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsInputRecordFilter = AnalyticsInputRecordFilter(),
        targetCurrency: Currency,
        groupBy: GroupingCriteria? = null,
    ): AnalyticsReportTO<InterestRateDataTO> {
        log.info { "Generating interest rate report for user $userId, interval=$interval, targetCurrency=$targetCurrency, groupBy=$groupBy" }

        val positionFilter = filter.toDbFilter(transactionTypes = listOf(TransactionType.OPEN_POSITION))
        val instrumentFilter = filter.toDbFilter(
            transactionTypes = listOf(TransactionType.OPEN_POSITION, TransactionType.CLOSE_POSITION),
            unitTypes = listOf(UnitType.INSTRUMENT),
        )

        val previousPositionRecords = analyticsRecordRepository.getRecordsBefore(userId, interval.from, positionFilter)
        val previousPositionsByGroup = previousPositionRecords.groupByKey(groupBy)

        val prevInstrumentUnits = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, instrumentFilter, groupBy)
        val bucketedInstrumentUnits = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, instrumentFilter, groupBy)

        val bucketPositionRecords = analyticsRecordRepository.getRecords(userId, interval, positionFilter)
        val bucketPositionsByGroup = bucketPositionRecords.groupByKey(groupBy)

        val allGroupKeys = (previousPositionsByGroup.keys + prevInstrumentUnits.groupKeys +
            bucketedInstrumentUnits.groupKeys + bucketPositionsByGroup.keys)
            .ifEmpty { setOf(GroupKey.Ungrouped) }

        val prevValuationDate = interval.from.date

        val initialStates = allGroupKeys.associateWith { groupKey ->
            val prevPositions = (previousPositionsByGroup[groupKey] ?: emptyList()).toPositions(targetCurrency)
            val prevInstruments = prevInstrumentUnits[groupKey]
            val prevValuation = convert(prevInstruments, targetCurrency, prevValuationDate)
            InterestRateState(
                allPositions = prevPositions,
                instrumentUnits = prevInstruments,
                previousValuation = prevValuation,
                previousValuationDate = prevValuationDate,
            )
        }.toMutableMap()

        val buckets = interval.generateBucketedData(initialStates) { dateTime, statesByGroup ->
            val instrumentBucket = bucketedInstrumentUnits.getBucket(dateTime)
            val valuationDate = dateTime.date

            val groupBuckets = statesByGroup.map { (groupKey, state) ->
                val bucketInstruments = instrumentBucket[groupKey]
                val totalInstrumentUnits = state.instrumentUnits + bucketInstruments
                val valuation = convert(totalInstrumentUnits, targetCurrency, valuationDate)

                val currentBucketRecords = (bucketPositionsByGroup[groupKey] ?: emptyList())
                    .filter { it.dateTime >= dateTime }
                val currentPositions = currentBucketRecords
                    .filter { it.unit.type == UnitType.CURRENCY }
                    .map { record ->
                        val rate = getRate(record.unit, targetCurrency, record.dateTime.date)
                        InterestRateCalculationCommand.Position(
                            date = record.dateTime.date,
                            amount = record.amount.negate().toJavaBigDecimal() * rate,
                        )
                    }

                val allPositions = state.allPositions + currentPositions
                val totalInterestRate = calculateRate(allPositions, valuation.toJavaBigDecimal(), valuationDate)
                val previousAggregated = InterestRateCalculationCommand.Position(state.previousValuationDate, state.previousValuation.toJavaBigDecimal())
                val currentInterestRate = calculateRate(currentPositions + previousAggregated, valuation.toJavaBigDecimal(), valuationDate)

                statesByGroup[groupKey] = InterestRateState(
                    allPositions = allPositions,
                    instrumentUnits = totalInstrumentUnits,
                    previousValuation = valuation,
                    previousValuationDate = valuationDate,
                )

                AnalyticsGroupBucketTO(
                    groupKey = groupKey.apiValue,
                    value = InterestRateDataTO(
                        totalInterestRate = BigDecimal.parseString(totalInterestRate.toPlainString()),
                        currentInterestRate = BigDecimal.parseString(currentInterestRate.toPlainString()),
                    ),
                )
            }
            AnalyticsBucketTO(dateTime, groupBuckets) to statesByGroup
        }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    private fun calculateRate(
        positions: List<InterestRateCalculationCommand.Position>,
        valuation: java.math.BigDecimal,
        valuationDate: LocalDate,
    ): java.math.BigDecimal {
        if (positions.none { it.date < valuationDate } || valuation <= java.math.BigDecimal.ZERO) {
            return java.math.BigDecimal.ZERO
        }
        return interestRateCalculator.calculateInterestRate(
            InterestRateCalculationCommand(positions = positions, valuation = valuation, valuationDate = valuationDate)
        )
    }

    private suspend fun convert(amounts: UnitAmounts, targetCurrency: Currency, date: LocalDate): BigDecimal {
        if (amounts.units.isEmpty()) return BigDecimal.ZERO
        val request = ConversionsRequest(amounts.units.map { ConversionRequest(it, targetCurrency, date) })
        val rates = conversionSdk.convert(request)
        return amounts.entries.fold(BigDecimal.ZERO) { acc, (unit, amount) ->
            val rate = rates.getRate(unit, targetCurrency, date)
            if (rate == null) {
                log.warn { "Conversion rate not found for $unit -> $targetCurrency on $date" }
                return@fold acc
            }
            acc + amount * rate
        }
    }

    private suspend fun getRate(source: FinancialUnit, target: Currency, date: LocalDate): java.math.BigDecimal {
        val response = conversionSdk.convert(ConversionsRequest(listOf(ConversionRequest(source, target, date))))
        return response.getRate(source, target, date)?.toJavaBigDecimal() ?: java.math.BigDecimal.ONE
    }

    private fun List<AnalyticsRecord>.toPositions(targetCurrency: Currency): List<InterestRateCalculationCommand.Position> =
        filter { it.unit.type == UnitType.CURRENCY }
            .map { record ->
                InterestRateCalculationCommand.Position(
                    date = record.dateTime.date,
                    amount = record.amount.negate().toJavaBigDecimal(),
                )
            }

    private fun List<AnalyticsRecord>.groupByKey(groupBy: GroupingCriteria?): Map<GroupKey, List<AnalyticsRecord>> =
        if (groupBy != null) groupBy { it.toGroupKey(groupBy) }
        else mapOf(GroupKey.Ungrouped to this)

    private fun AnalyticsRecord.toGroupKey(groupBy: GroupingCriteria): GroupKey = when (groupBy) {
        GroupingCriteria.FINANCIAL_UNIT -> GroupKey.ByFinancialUnit(unit.value)
        GroupingCriteria.ACCOUNT -> GroupKey.ByAccount(accountId.toString())
        GroupingCriteria.FUND -> GroupKey.ByFund(fundId.toString())
        GroupingCriteria.CATEGORY -> GroupKey.ByCategory(category?.value)
    }
}
