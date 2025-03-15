package ro.jf.funds.reporting.service.service

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.api.model.TimeGranularity
import ro.jf.funds.reporting.service.domain.BucketData

fun GranularDateInterval.getBucket(date: LocalDate): DateInterval =
    DateInterval(getBucketStart(date), getBucketEnd(date))

fun GranularDateInterval.getBuckets(): Sequence<DateInterval> {
    return generateSequence(
        seed = firstBucket(),
        nextFunction = { previous: DateInterval -> getNextBucket(previous) }
    )
}

fun <D> GranularDateInterval.generateBucketedData(
    seedFunction: (DateInterval) -> D,
    nextFunction: (DateInterval, D) -> D,
): Map<DateInterval, D> {
    return generateSequence(
        seedFunction = { firstBucket().let { it to seedFunction(it) } },
        nextFunction = { (previousBucket, previousData) ->
            getNextBucket(previousBucket)?.let { it to nextFunction(it, previousData) }
        }
    ).toMap()
}

private fun GranularDateInterval.getNextBucket(previousBucket: DateInterval): DateInterval? {
    val nextBucketStart = previousBucket.to.plus(DatePeriod(days = 1))
    val nextBucketEnd = getBucketEnd(nextBucketStart)
    return if (nextBucketStart > interval.to) {
        null
    } else {
        DateInterval(nextBucketStart, nextBucketEnd)
    }
}

private fun GranularDateInterval.firstBucket(): DateInterval =
    DateInterval(getBucketStart(interval.from), getBucketEnd(interval.from))

private fun GranularDateInterval.getBucketStart(date: LocalDate): LocalDate {
    val bucketLogicalStart = when (granularity) {
        TimeGranularity.DAILY -> LocalDate(date.year, date.monthNumber, date.dayOfMonth)
        TimeGranularity.MONTHLY -> LocalDate(date.year, date.monthNumber, 1)
        TimeGranularity.YEARLY -> LocalDate(date.year, 1, 1)
    }
    return maxOf(bucketLogicalStart, interval.from)
}

private fun GranularDateInterval.getBucketEnd(date: LocalDate): LocalDate {
    val bucketLogicalEnd = when (granularity) {
        TimeGranularity.DAILY -> LocalDate(date.year, date.monthNumber, date.dayOfMonth)
        TimeGranularity.MONTHLY -> LocalDate(date.year, date.monthNumber, 1)
            .plus(DatePeriod(months = 1))
            .minus(DatePeriod(days = 1))

        TimeGranularity.YEARLY -> LocalDate(date.year, 12, 31)
    }
    return minOf(bucketLogicalEnd, interval.to)
}
