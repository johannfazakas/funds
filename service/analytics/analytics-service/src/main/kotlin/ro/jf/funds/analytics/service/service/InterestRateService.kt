package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toJavaBigDecimal
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.*
import ro.jf.funds.analytics.service.domain.*
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
    suspend fun getInterestRateReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
        targetCurrency: Currency,
        groupBy: GroupingCriteria? = null,
    ): AnalyticsReportTO<InterestRateDataTO> {
        log.info { "Generating interest rate report for user $userId, interval=$interval, targetCurrency=$targetCurrency" }
        return getUngroupedInterestRateReport(userId, interval, filter, targetCurrency)
    }

    private data class InterestRateState(
        val allPositions: List<InterestRateCalculationCommand.Position>,
        val instrumentUnits: UnitAmounts,
        val previousValuation: BigDecimal,
        val previousValuationDate: LocalDate,
    )

    private suspend fun getUngroupedInterestRateReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter,
        targetCurrency: Currency,
    ): AnalyticsReportTO<InterestRateDataTO> {
        val positionFilter = filter.copy(transactionTypes = listOf(TransactionType.OPEN_POSITION))
        val instrumentFilter = filter.copy(
            transactionTypes = listOf(TransactionType.OPEN_POSITION, TransactionType.CLOSE_POSITION),
            units = filter.units.filter { it.type == UnitType.INSTRUMENT },
        )

        val previousPositionRecords = analyticsRecordRepository.getRecordsBefore(userId, interval.from, positionFilter)
        val previousPositions = previousPositionRecords.toPositions(targetCurrency)

        val prevInstrumentUnits = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, instrumentFilter)
        val bucketedInstrumentUnits = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, instrumentFilter)

        val bucketPositionRecords = analyticsRecordRepository.getRecords(userId, interval, positionFilter)

        val prevValuationDate = interval.from.date
        val prevValuation = convert(prevInstrumentUnits, targetCurrency, prevValuationDate)

        val initialState = InterestRateState(
            allPositions = previousPositions,
            instrumentUnits = prevInstrumentUnits,
            previousValuation = prevValuation,
            previousValuationDate = prevValuationDate,
        )

        val buckets = interval.generateBucketedData(initialState) { dateTime, state ->
            val bucketInstruments = bucketedInstrumentUnits.getBucket(dateTime)
            val totalInstrumentUnits = state.instrumentUnits + bucketInstruments

            val valuationDate = dateTime.date
            val valuation = convert(totalInstrumentUnits, targetCurrency, valuationDate)

            val currentBucketRecords = bucketPositionRecords.filter { record ->
                record.dateTime >= dateTime
            }
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

            val data = InterestRateDataTO(
                totalInterestRate = BigDecimal.parseString(totalInterestRate.toPlainString()),
                currentInterestRate = BigDecimal.parseString(currentInterestRate.toPlainString()),
            )

            val nextState = InterestRateState(
                allPositions = allPositions,
                instrumentUnits = totalInstrumentUnits,
                previousValuation = valuation,
                previousValuationDate = valuationDate,
            )

            AnalyticsBucketTO(dateTime, listOf(AnalyticsGroupBucketTO(value = data))) to nextState
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
}
