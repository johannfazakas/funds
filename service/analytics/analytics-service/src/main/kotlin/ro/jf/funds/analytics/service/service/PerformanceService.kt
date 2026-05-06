package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.*
import ro.jf.funds.analytics.service.domain.AnalyticsRecordFilter
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.UnitAmounts
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.UnitType

private val log = logger { }

class PerformanceService(
    private val analyticsRecordRepository: AnalyticsRecordRepository,
    private val conversionSdk: ConversionSdk,
) {
    suspend fun getPerformanceReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
        targetCurrency: Currency,
        groupBy: GroupingCriteria? = null,
    ): AnalyticsReportTO<PerformanceDataTO> {
        log.info { "Generating performance report for user $userId, interval=$interval, targetCurrency=$targetCurrency" }
        return getUngroupedPerformanceReport(userId, interval, filter, targetCurrency)
    }

    private suspend fun getUngroupedPerformanceReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter,
        targetCurrency: Currency,
    ): AnalyticsReportTO<PerformanceDataTO> {
        val investmentFilter = AnalyticsRecordFilter(
            fundIds = filter.fundIds,
            transactionTypes = listOf(TransactionType.OPEN_POSITION),
        )
        val instrumentFilter = AnalyticsRecordFilter(
            fundIds = filter.fundIds,
            transactionTypes = listOf(TransactionType.OPEN_POSITION, TransactionType.CLOSE_POSITION),
        )
        val currencyFilter = AnalyticsRecordFilter(fundIds = filter.fundIds)

        val previousInvestment = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, investmentFilter)
        val previousInstruments = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, instrumentFilter)
        val previousCurrency = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, currencyFilter)

        val bucketedInvestment = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, investmentFilter)
        val bucketedInstruments = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, instrumentFilter)
        val bucketedCurrency = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, currencyFilter)

        data class State(
            val investment: UnitAmounts,
            val instruments: UnitAmounts,
            val currency: UnitAmounts,
            val previousTotalProfit: BigDecimal,
        )

        val seed = State(previousInvestment, previousInstruments, previousCurrency, BigDecimal.ZERO)

        val buckets = interval.generateBucketedData(seed) { dateTime, state ->
            val currentBucketInvestment = bucketedInvestment.getBucket(dateTime)
            val updatedState = State(
                investment = state.investment + currentBucketInvestment,
                instruments = state.instruments + bucketedInstruments.getBucket(dateTime),
                currency = state.currency + bucketedCurrency.getBucket(dateTime),
                previousTotalProfit = BigDecimal.ZERO,
            )

            val totalInvestment = convertCurrencyUnits(updatedState.investment, targetCurrency, dateTime.date).negate()
            val currentInvestment = convertCurrencyUnits(currentBucketInvestment, targetCurrency, dateTime.date).negate()
            val totalInstrumentValue = convertAll(updatedState.instruments, targetCurrency, dateTime.date)
            val currencyValue = convertCurrencyUnits(updatedState.currency, targetCurrency, dateTime.date)
            val totalProfit = totalInstrumentValue - totalInvestment
            val currentProfit = totalProfit - state.previousTotalProfit

            val data = PerformanceDataTO(
                totalInvestment = totalInvestment,
                currentInvestment = currentInvestment,
                totalProfit = totalProfit,
                currentProfit = currentProfit,
                totalInstrumentValue = totalInstrumentValue,
                currencyValue = currencyValue,
            )

            val nextState = updatedState.copy(previousTotalProfit = totalProfit)
            AnalyticsBucketTO(dateTime, listOf(AnalyticsGroupBucketTO(value = data))) to nextState
        }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    private suspend fun convertCurrencyUnits(
        amounts: UnitAmounts, targetCurrency: Currency, date: LocalDate,
    ): BigDecimal {
        val currencyEntries = amounts.entries.filter { it.key.type == UnitType.CURRENCY }
        if (currencyEntries.isEmpty()) return BigDecimal.ZERO
        val request = ConversionsRequest(currencyEntries.map { ConversionRequest(it.key, targetCurrency, date) })
        val rates = conversionSdk.convert(request)
        return currencyEntries.fold(BigDecimal.ZERO) { acc, (unit, amount) ->
            val rate = rates.getRate(unit, targetCurrency, date)
            if (rate == null) {
                log.warn { "Conversion rate not found for $unit -> $targetCurrency on $date, treating as zero" }
                return@fold acc
            }
            acc + amount * rate
        }
    }

    private suspend fun convertAll(
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

}
