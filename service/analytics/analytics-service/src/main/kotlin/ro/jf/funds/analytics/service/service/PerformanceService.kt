package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.*
import ro.jf.funds.analytics.service.domain.AnalyticsInputRecordFilter
import ro.jf.funds.analytics.service.domain.GroupKey
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
    private data class PerformanceState(
        val investment: UnitAmounts,
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

        val investmentFilter = filter.toDbFilter(transactionTypes = listOf(TransactionType.OPEN_POSITION))
        val instrumentFilter = filter.toDbFilter(transactionTypes = listOf(TransactionType.OPEN_POSITION, TransactionType.CLOSE_POSITION))
        val currencyFilter = filter.toDbFilter()

        val prevInvestment = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, investmentFilter, groupBy)
        val prevInstruments = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, instrumentFilter, groupBy)
        val prevCurrency = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, currencyFilter, groupBy)

        val bucketedInvestment = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, investmentFilter, groupBy)
        val bucketedInstruments = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, instrumentFilter, groupBy)
        val bucketedCurrency = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, currencyFilter, groupBy)

        val allGroupKeys = (prevInvestment.groupKeys + prevInstruments.groupKeys + prevCurrency.groupKeys +
            bucketedInvestment.groupKeys + bucketedInstruments.groupKeys + bucketedCurrency.groupKeys)
            .ifEmpty { setOf(GroupKey.Ungrouped) }

        val initialStates = allGroupKeys.associateWith { groupKey ->
            PerformanceState(
                investment = prevInvestment[groupKey],
                instruments = prevInstruments[groupKey],
                currency = prevCurrency[groupKey],
                previousTotalProfit = BigDecimal.ZERO,
            )
        }.toMutableMap()

        val buckets = interval.generateBucketedData(initialStates) { dateTime, statesByGroup ->
            val investmentBucket = bucketedInvestment.getBucket(dateTime)
            val instrumentsBucket = bucketedInstruments.getBucket(dateTime)
            val currencyBucket = bucketedCurrency.getBucket(dateTime)

            val groupBuckets = statesByGroup.map { (groupKey, state) ->
                val currentBucketInvestment = investmentBucket[groupKey]
                val updatedState = PerformanceState(
                    investment = state.investment + currentBucketInvestment,
                    instruments = state.instruments + instrumentsBucket[groupKey],
                    currency = state.currency + currencyBucket[groupKey],
                    previousTotalProfit = BigDecimal.ZERO,
                )

                val totalInvestment = convertCurrencyUnits(updatedState.investment, targetCurrency, dateTime.date).negate()
                val currentInvestment = convertCurrencyUnits(currentBucketInvestment, targetCurrency, dateTime.date).negate()
                val totalInstrumentValue = convertInstrumentUnits(updatedState.instruments, targetCurrency, dateTime.date)
                val currencyValue = convertCurrencyUnits(updatedState.currency, targetCurrency, dateTime.date)
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

    private suspend fun convertInstrumentUnits(
        amounts: UnitAmounts, targetCurrency: Currency, date: LocalDate,
    ): BigDecimal {
        val instrumentEntries = amounts.entries.filter { it.key.type == UnitType.INSTRUMENT }
        if (instrumentEntries.isEmpty()) return BigDecimal.ZERO
        val request = ConversionsRequest(instrumentEntries.map { ConversionRequest(it.key, targetCurrency, date) })
        val rates = conversionSdk.convert(request)
        return instrumentEntries.fold(BigDecimal.ZERO) { acc, (unit, amount) ->
            val rate = rates.getRate(unit, targetCurrency, date)
            if (rate == null) {
                log.warn { "Conversion rate not found for $unit -> $targetCurrency on $date, treating as zero" }
                return@fold acc
            }
            acc + amount * rate
        }
    }
}
