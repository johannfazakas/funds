package ro.jf.funds.reporting.api.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

@Serializable
data class GranularDateInterval(
    val interval: DateInterval,
    val granularity: TimeGranularity,
) {
    fun <D> generateBucketedData(
        seedFunction: (DateInterval) -> D,
        nextFunction: (DateInterval, D) -> D,
    ): List<D> {
        val firstBucketData = {
            val timeBucket = firstBucket()
            timeBucket to seedFunction(timeBucket)
        }
        val nextBucketData: (Pair<DateInterval, D>) -> Pair<DateInterval, D>? = { (previousBucket, previousData) ->
            val nextBucketStart = previousBucket.to.plus(DatePeriod(days = 1))
            val nextBucketEnd = getBucketEnd(nextBucketStart)
            if (nextBucketStart > interval.to) {
                null
            } else {
                val nextBucket = DateInterval(nextBucketStart, nextBucketEnd)
                nextBucket to nextFunction(nextBucket, previousData)
            }
        }
        return generateSequence(firstBucketData, nextBucketData).map { it.second }.toList()
    }

    fun getBuckets(): List<DateInterval> {
        val firstBucket = firstBucket()
        val nextBucket = { previous: DateInterval ->
            val nextBucketStart = previous.to.plus(DatePeriod(days = 1))
            val nextBucketEnd = getBucketEnd(nextBucketStart)
            if (nextBucketStart > interval.to) {
                null
            } else {
                DateInterval(nextBucketStart, nextBucketEnd)
            }
        }

        return generateSequence(firstBucket, nextBucket).toList()
    }

    fun getBucket(date: LocalDate): DateInterval = DateInterval(getBucketStart(date), getBucketEnd(date))

    private fun getBucketStart(date: LocalDate): LocalDate {
        val bucketLogicalStart = when (granularity) {
            TimeGranularity.DAILY -> LocalDate(date.year, date.monthNumber, date.dayOfMonth)
            TimeGranularity.MONTHLY -> LocalDate(date.year, date.monthNumber, 1)
            TimeGranularity.YEARLY -> LocalDate(date.year, 1, 1)
        }
        return maxOf(bucketLogicalStart, interval.from)
    }

    private fun getBucketEnd(date: LocalDate): LocalDate {
        val bucketLogicalEnd = when (granularity) {
            TimeGranularity.DAILY -> LocalDate(date.year, date.monthNumber, date.dayOfMonth)
            TimeGranularity.MONTHLY -> LocalDate(date.year, date.monthNumber, 1)
                .plus(DatePeriod(months = 1))
                .minus(DatePeriod(days = 1))

            TimeGranularity.YEARLY -> LocalDate(date.year, 12, 31)
        }
        return minOf(bucketLogicalEnd, interval.to)
    }

    private fun firstBucket(): DateInterval = DateInterval(getBucketStart(interval.from), getBucketEnd(interval.from))
}

@Serializable
data class DateInterval(
    val from: LocalDate,
    val to: LocalDate,
) {
    init {
        require(from <= to) { "From date must be before or equal to the to date" }
    }
}
