package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.AnalyticsBucketTO
import ro.jf.funds.analytics.api.model.AnalyticsGroupBucketTO
import ro.jf.funds.analytics.api.model.AnalyticsReportTO
import ro.jf.funds.analytics.api.model.GroupingCriteria
import ro.jf.funds.analytics.service.domain.AnalyticsRecordFilter
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.UnitAmounts
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
        groupBy: GroupingCriteria? = null,
    ): AnalyticsReportTO {
        log.info { "Generating balance report for user $userId, interval=$interval, targetCurrency=$targetCurrency, groupBy=$groupBy" }
        return if (groupBy != null)
            getGroupedBalanceReport(userId, interval, filter, targetCurrency, groupBy)
        else
            getUngroupedBalanceReport(userId, interval, filter, targetCurrency)
    }

    suspend fun getNetChangeReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
        targetCurrency: Currency,
        groupBy: GroupingCriteria? = null,
    ): AnalyticsReportTO {
        log.info { "Generating net change report for user $userId, interval=$interval, targetCurrency=$targetCurrency, groupBy=$groupBy" }
        return if (groupBy != null)
            getGroupedNetChangeReport(userId, interval, filter, targetCurrency, groupBy)
        else
            getUngroupedNetChangeReport(userId, interval, filter, targetCurrency)
    }

    private suspend fun getUngroupedBalanceReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter,
        targetCurrency: Currency,
    ): AnalyticsReportTO {
        val previousBalance = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, filter)
        val bucketedUnitAmounts = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, filter)

        val conversionRates =
            fetchConversionRates(previousBalance.units + bucketedUnitAmounts.units, interval, targetCurrency)

        val buckets = interval.generateBucketedData(previousBalance) { dateTime, balance ->
            val convertedTotal = convert(balance, targetCurrency, dateTime.date, conversionRates)
            val updatedBalance = balance + bucketedUnitAmounts.getBucket(dateTime)
            AnalyticsBucketTO(dateTime, listOf(AnalyticsGroupBucketTO(value = convertedTotal))) to updatedBalance
        }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    private suspend fun getGroupedBalanceReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter,
        targetCurrency: Currency,
        groupBy: GroupingCriteria,
    ): AnalyticsReportTO {
        val previousBalances =
            analyticsRecordRepository.getGroupedUnitAmountsBefore(userId, interval.from, filter, groupBy)
        val bucketedGroupedUnitAmounts =
            analyticsRecordRepository.getBucketedGroupedUnitAmounts(userId, interval, filter, groupBy)

        val allUnits = previousBalances.units + bucketedGroupedUnitAmounts.units
        val conversionRates = fetchConversionRates(allUnits, interval, targetCurrency)

        val allGroupKeys = previousBalances.groupKeys + bucketedGroupedUnitAmounts.groupKeys
        val initialBalances = allGroupKeys.associateWith { groupKey ->
            previousBalances[groupKey]
        }.toMutableMap()

        val buckets = interval.generateBucketedData(initialBalances) { dateTime, balancesByGroup ->
            val groupBuckets = balancesByGroup.map { (groupKey, balance) ->
                AnalyticsGroupBucketTO(
                    groupKey = groupKey,
                    value = convert(balance, targetCurrency, dateTime.date, conversionRates)
                )
            }
            val bucketAggregates = bucketedGroupedUnitAmounts.getBucket(dateTime)
            for ((groupKey, amounts) in bucketAggregates) {
                val current = balancesByGroup[groupKey] ?: UnitAmounts.EMPTY
                balancesByGroup[groupKey] = current + amounts
            }
            AnalyticsBucketTO(dateTime, groupBuckets) to balancesByGroup
        }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    private suspend fun getUngroupedNetChangeReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter,
        targetCurrency: Currency,
    ): AnalyticsReportTO {
        val bucketedUnitAmounts = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, filter)
        val conversionRates = fetchConversionRates(bucketedUnitAmounts.units, interval, targetCurrency)

        val buckets = interval.generateBucketedData { dateTime ->
            val convertedTotal = convert(bucketedUnitAmounts.getBucket(dateTime), targetCurrency, dateTime.date, conversionRates)
            AnalyticsBucketTO(dateTime, listOf(AnalyticsGroupBucketTO(value = convertedTotal)))
        }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    private suspend fun getGroupedNetChangeReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter,
        targetCurrency: Currency,
        groupBy: GroupingCriteria,
    ): AnalyticsReportTO {
        val bucketedGroupedUnitAmounts =
            analyticsRecordRepository.getBucketedGroupedUnitAmounts(userId, interval, filter, groupBy)
        val conversionRates = fetchConversionRates(bucketedGroupedUnitAmounts.units, interval, targetCurrency)

        val buckets = interval.generateBucketedData { dateTime ->
            val bucketGroups = bucketedGroupedUnitAmounts.getBucket(dateTime)
            val groupBuckets = bucketGroups.map { (groupKey, amounts) ->
                AnalyticsGroupBucketTO(
                    groupKey = groupKey,
                    value = convert(amounts, targetCurrency, dateTime.date, conversionRates)
                )
            }
            AnalyticsBucketTO(dateTime, groupBuckets)
        }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
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
        amounts: UnitAmounts,
        targetCurrency: Currency,
        date: LocalDate,
        conversionRates: ConversionsResponse,
    ): BigDecimal = amounts.entries.fold(BigDecimal.ZERO) { acc, (unit, balance) ->
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
