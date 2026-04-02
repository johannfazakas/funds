package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.AnalyticsBucketTO
import ro.jf.funds.analytics.api.model.AnalyticsReportTO
import ro.jf.funds.analytics.service.domain.AnalyticsRecordFilter
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit

private val log = logger { }

class AnalyticsService(
    private val analyticsRecordRepository: AnalyticsRecordRepository,
    private val conversionSdk: ConversionSdk,
) {
    suspend fun getBalanceReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
        targetCurrency: Currency,
    ): AnalyticsReportTO {
        log.info { "Generating balance report for user $userId, interval=$interval, targetCurrency=$targetCurrency" }
        val previousBalance = analyticsRecordRepository.getBalanceBefore(userId, interval.from, filter)
        val aggregates = analyticsRecordRepository.getValueAggregatesByUnit(userId, interval, filter)

        val conversionRates = fetchConversionRates(previousBalance.keys + aggregates.units, interval, targetCurrency)

        val buckets = interval.generateBucketedData(previousBalance) { dateTime, balancesByUnit ->
            val convertedTotal = convert(balancesByUnit, targetCurrency, dateTime.date, conversionRates)
            val updatedBalances = sum(balancesByUnit, aggregates.getBucket(dateTime))
            AnalyticsBucketTO(dateTime, convertedTotal) to updatedBalances
        }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    suspend fun getNetChangeReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
        targetCurrency: Currency,
    ): AnalyticsReportTO {
        log.info { "Generating net change report for user $userId, interval=$interval, targetCurrency=$targetCurrency" }
        val aggregates = analyticsRecordRepository.getValueAggregatesByUnit(userId, interval, filter)

        val conversionRates = fetchConversionRates(aggregates.units, interval, targetCurrency)

        val buckets = interval.generateBucketedData { dateTime ->
            AnalyticsBucketTO(dateTime, convert(aggregates.getBucket(dateTime), targetCurrency, dateTime.date, conversionRates))
        }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    private fun sum(
        balances: Map<FinancialUnit, BigDecimal>,
        netChanges: Map<FinancialUnit, BigDecimal>,
    ): Map<FinancialUnit, BigDecimal> =
        (balances.keys + netChanges.keys).associateWith { unit ->
            (balances[unit] ?: BigDecimal.ZERO) + (netChanges[unit] ?: BigDecimal.ZERO)
        }

    private suspend fun fetchConversionRates(
        units: Set<FinancialUnit>,
        interval: ReportInterval,
        targetCurrency: Currency,
    ): ConversionsResponse {
        val dates = interval.generateBucketDates().map { it.date }.toSet()
        val conversionRequests = units
            .filter { it != targetCurrency }
            .flatMap { unit -> dates.map { date -> ConversionRequest(unit, targetCurrency, date) } }
        return conversionSdk.convert(ConversionsRequest(conversionRequests))
    }


    private fun convert(
        amountByUnit: Map<FinancialUnit, BigDecimal>,
        targetCurrency: Currency,
        date: LocalDate,
        conversionRates: ConversionsResponse,
    ): BigDecimal = amountByUnit.entries.fold(BigDecimal.ZERO) { acc, (unit, balance) ->
        acc + convert(balance, unit, targetCurrency, date, conversionRates)
    }

    private fun convert(
        amount: BigDecimal,
        sourceUnit: FinancialUnit,
        targetCurrency: Currency,
        date: LocalDate,
        conversionRates: ConversionsResponse,
    ): BigDecimal {
        if (sourceUnit == targetCurrency) return amount
        val rate = conversionRates.getRate(sourceUnit, targetCurrency, date)
        if (rate == null) {
            log.warn { "Conversion rate not found for $sourceUnit -> $targetCurrency on $date, treating as zero" }
            return BigDecimal.ZERO
        }
        return amount * rate
    }
}
