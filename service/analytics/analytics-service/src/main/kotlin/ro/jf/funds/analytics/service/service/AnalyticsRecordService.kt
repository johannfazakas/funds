package ro.jf.funds.analytics.service.service

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.*
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.AnalyticsValueBucketTO
import ro.jf.funds.analytics.api.model.AnalyticsValueReportTO
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.domain.AnalyticsRecordFilter
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import com.benasher44.uuid.Uuid

private val log = logger { }

class AnalyticsRecordService(
    private val analyticsRecordRepository: AnalyticsRecordRepository,
) {
    suspend fun getValueReport(
        userId: Uuid,
        granularity: TimeGranularity,
        from: LocalDateTime,
        to: LocalDateTime,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
    ): AnalyticsValueReportTO {
        log.info { "Generating value report for user $userId, granularity=$granularity, from=$from, to=$to" }
        val baseline = analyticsRecordRepository.getSumBefore(userId, from, filter)
        val aggregatesByBucket = analyticsRecordRepository
            .getValueAggregates(userId, granularity, from, to, filter)
            .associateBy { it.dateTime }
        val firstBucket = truncate(from, granularity)
        val firstNetChange = aggregatesByBucket[firstBucket]?.sum ?: BigDecimal.ZERO
        val firstBalance = baseline + firstNetChange
        val seed = firstBalance to AnalyticsValueBucketTO(firstBucket, firstNetChange, firstBalance)

        val buckets = generateSequence(seed) { (prevBalance, prevBucket) ->
            val next = advance(prevBucket.dateTime, granularity)
            if (next >= to) return@generateSequence null
            val netChange = aggregatesByBucket[next]?.sum ?: BigDecimal.ZERO
            val balance = prevBalance + netChange
            balance to AnalyticsValueBucketTO(next, netChange, balance)
        }.map { it.second }.toList()

        return AnalyticsValueReportTO(
            granularity = granularity,
            buckets = buckets,
        )
    }

    private fun truncate(dateTime: LocalDateTime, granularity: TimeGranularity): LocalDateTime {
        val date = dateTime.date
        return when (granularity) {
            TimeGranularity.DAILY -> LocalDateTime(date, LocalTime(0, 0))
            TimeGranularity.WEEKLY -> {
                val daysFromMonday = date.dayOfWeek.isoDayNumber - 1
                LocalDateTime(date.minus(daysFromMonday, DateTimeUnit.DAY), LocalTime(0, 0))
            }
            TimeGranularity.MONTHLY -> LocalDateTime(date.year, date.monthNumber, 1, 0, 0)
            TimeGranularity.YEARLY -> LocalDateTime(date.year, 1, 1, 0, 0)
        }
    }

    private fun advance(dateTime: LocalDateTime, granularity: TimeGranularity): LocalDateTime {
        val date = dateTime.date
        val nextDate = when (granularity) {
            TimeGranularity.DAILY -> date.plus(1, DateTimeUnit.DAY)
            TimeGranularity.WEEKLY -> date.plus(1, DateTimeUnit.WEEK)
            TimeGranularity.MONTHLY -> date.plus(1, DateTimeUnit.MONTH)
            TimeGranularity.YEARLY -> date.plus(1, DateTimeUnit.YEAR)
        }
        return LocalDateTime(nextDate, LocalTime(0, 0))
    }
}
