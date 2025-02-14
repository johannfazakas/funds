package ro.jf.funds.reporting.api.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

// TODO(Johann) check if it will be actually needed
fun getTimeBucketStart(date: LocalDate, granularity: TimeGranularity): LocalDate = when (granularity) {
    TimeGranularity.DAILY -> LocalDate(date.year, date.monthNumber, date.dayOfMonth)
    TimeGranularity.MONTHLY -> LocalDate(date.year, date.monthNumber, 1)
    TimeGranularity.YEARLY -> LocalDate(date.year, 1, 1)
}

fun getTimeBucketEnd(date: LocalDate, granularity: TimeGranularity): LocalDate = when (granularity) {
    TimeGranularity.DAILY -> LocalDate(date.year, date.monthNumber, date.dayOfMonth)
    TimeGranularity.MONTHLY -> LocalDate(date.year, date.monthNumber, 1)
        .plus(DatePeriod(months = 1))
        .minus(DatePeriod(days = 1))

    TimeGranularity.YEARLY -> LocalDate(date.year, 12, 31)
}

@Serializable
data class GranularDateInterval(
    val interval: DateInterval,
    val granularity: TimeGranularity,
) {
    fun <D> generateBucketedData(
        seedFunction: (DateInterval) -> D,
        nextFunction: (DateInterval, D) -> D,
    ): List<Pair<DateInterval, D>> {
        val firstBucketData = {
            val timeBucket = firstBucket()
            timeBucket to seedFunction(timeBucket)
        }
        val nextBucketData: (Pair<DateInterval, D>) -> Pair<DateInterval, D>? = { (previousBucket, previousData) ->
            val nextBucketStart = previousBucket.to.plus(DatePeriod(days = 1))
            val nextBucketEnd = minOf(getTimeBucketEnd(nextBucketStart, granularity), interval.to)
            if (nextBucketStart > interval.to) {
                null
            } else {
                val nextBucket = DateInterval(nextBucketStart, nextBucketEnd)
                nextBucket to nextFunction(nextBucket, previousData)
            }
        }
        return generateSequence(firstBucketData, nextBucketData).toList()
    }

    fun getBuckets(): List<DateInterval> {
        val firstBucket = firstBucket()
        val nextBucket = { previous: DateInterval ->
            val nextBucketStart = previous.to.plus(DatePeriod(days = 1))
            val nextBucketEnd = minOf(getTimeBucketEnd(nextBucketStart, granularity), interval.to)
            if (nextBucketStart > interval.to) {
                null
            } else {
                DateInterval(nextBucketStart, nextBucketEnd)
            }
        }

        return generateSequence(firstBucket, nextBucket).toList()
    }

    fun firstBucket(): DateInterval {
        val from = maxOf(getTimeBucketStart(interval.from, granularity), interval.from)
        val to = minOf(getTimeBucketEnd(interval.from, granularity), interval.to)
        return DateInterval(from, to)
    }
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
