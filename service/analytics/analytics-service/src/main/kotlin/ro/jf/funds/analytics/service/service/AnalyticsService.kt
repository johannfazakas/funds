package ro.jf.funds.analytics.service.service

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.*
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.AnalyticsBucketTO
import ro.jf.funds.analytics.api.model.AnalyticsReportTO
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.AnalyticsRecordFilter
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import com.benasher44.uuid.Uuid

private val log = logger { }

class AnalyticsService(
    private val analyticsRecordRepository: AnalyticsRecordRepository,
) {
    suspend fun getBalanceReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
    ): AnalyticsReportTO {
        log.info { "Generating balance report for user $userId, interval=$interval" }
        val baseline = analyticsRecordRepository.getSumBefore(userId, interval.from, filter)
        val netChangeByBucket = netChangeResolver(userId, interval, filter)
        val buckets = interval.generateBucketedData(baseline) { dateTime, runningBalance ->
            runningBalance + netChangeByBucket(dateTime)
        }.map { (dateTime, balance) -> AnalyticsBucketTO(dateTime, balance) }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    suspend fun getNetChangeReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
    ): AnalyticsReportTO {
        log.info { "Generating net change report for user $userId, interval=$interval" }
        val netChangeByBucket = netChangeResolver(userId, interval, filter)
        val buckets = interval.generateBucketedData { dateTime ->
            netChangeByBucket(dateTime)
        }.map { (dateTime, netChange) -> AnalyticsBucketTO(dateTime, netChange) }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    private suspend fun netChangeResolver(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter,
    ): (LocalDateTime) -> BigDecimal {
        val aggregatesByBucket = analyticsRecordRepository
            .getValueAggregates(userId, interval.granularity, interval.from, interval.to, filter)
            .associateBy { it.dateTime }
        return { dateTime -> aggregatesByBucket[interval.truncate(dateTime)]?.sum ?: BigDecimal.ZERO }
    }
}
