package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.AnalyticsBucketTO
import ro.jf.funds.analytics.api.model.AnalyticsGroupBucketTO
import ro.jf.funds.analytics.api.model.AnalyticsReportTO
import ro.jf.funds.analytics.api.model.GroupingCriteria
import ro.jf.funds.analytics.service.domain.AnalyticsInputRecordFilter
import ro.jf.funds.analytics.service.domain.GroupKey
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.UnitAmounts
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.platform.api.model.Currency

private val log = logger { }

class AnalyticsService(
    private val analyticsRecordRepository: AnalyticsRecordRepository,
    private val conversionSdk: ConversionSdk,
) {
    suspend fun getBalanceReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsInputRecordFilter = AnalyticsInputRecordFilter(),
        targetCurrency: Currency,
        groupBy: GroupingCriteria? = null,
    ): AnalyticsReportTO<BigDecimal> {
        log.info { "Generating balance report for user $userId, interval=$interval, targetCurrency=$targetCurrency, groupBy=$groupBy" }
        val dbFilter = filter.toDbFilter()
        val previousBalances = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, dbFilter, groupBy)
        val bucketedAmounts = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, dbFilter, groupBy)

        val allGroupKeys = (previousBalances.groupKeys + bucketedAmounts.groupKeys)
            .ifEmpty { setOf(GroupKey.Ungrouped) }
        val initialBalances = allGroupKeys.associateWith { groupKey ->
            previousBalances[groupKey]
        }.toMutableMap()

        val buckets = interval.generateBucketedData(initialBalances) { dateTime, balancesByGroup ->
            val groupBuckets = balancesByGroup.map { (groupKey, balance) ->
                AnalyticsGroupBucketTO(
                    groupKey = groupKey.apiValue,
                    value = convert(balance, targetCurrency, dateTime.date)
                )
            }
            val bucketAggregates = bucketedAmounts.getBucket(dateTime)
            for (groupKey in bucketAggregates.groupKeys) {
                val current = balancesByGroup[groupKey] ?: UnitAmounts.EMPTY
                balancesByGroup[groupKey] = current + bucketAggregates[groupKey]
            }
            AnalyticsBucketTO(dateTime, groupBuckets) to balancesByGroup
        }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    suspend fun getNetChangeReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsInputRecordFilter = AnalyticsInputRecordFilter(),
        targetCurrency: Currency,
        groupBy: GroupingCriteria? = null,
    ): AnalyticsReportTO<BigDecimal> {
        log.info { "Generating net change report for user $userId, interval=$interval, targetCurrency=$targetCurrency, groupBy=$groupBy" }
        val dbFilter = filter.toDbFilter()
        val bucketedAmounts = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, dbFilter, groupBy)

        val allGroupKeys = bucketedAmounts.groupKeys
            .ifEmpty { setOf(GroupKey.Ungrouped) }

        val buckets = interval.generateBucketedData { dateTime ->
            val bucketGroups = bucketedAmounts.getBucket(dateTime)
            val groupBuckets = allGroupKeys.map { groupKey ->
                val amounts = bucketGroups[groupKey]
                AnalyticsGroupBucketTO(
                    groupKey = groupKey.apiValue,
                    value = convert(amounts, targetCurrency, dateTime.date)
                )
            }
            AnalyticsBucketTO(dateTime, groupBuckets)
        }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    private suspend fun convert(
        amounts: UnitAmounts, targetCurrency: Currency, date: LocalDate,
    ): BigDecimal {
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
