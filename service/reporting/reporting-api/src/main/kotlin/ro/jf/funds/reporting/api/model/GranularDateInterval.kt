package ro.jf.funds.reporting.api.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

fun getTimeBucket(date: LocalDate, granularity: TimeGranularity): LocalDate = when (granularity) {
    TimeGranularity.DAILY -> LocalDate(date.year, date.monthNumber, date.dayOfMonth)
    TimeGranularity.MONTHLY -> LocalDate(date.year, date.monthNumber, 1)
    TimeGranularity.YEARLY -> LocalDate(date.year, 1, 1)
}

fun <D> generateTimeBucketedData(
    interval: GranularDateInterval,
    seedFunction: (LocalDate) -> D,
    nextFunction: (LocalDate, D) -> D,
): List<Pair<LocalDate, D>> {
    val firstBucketData = {
        val timeBucket = getTimeBucket(interval.interval.from, interval.granularity)
        timeBucket to seedFunction(timeBucket)
    }
    val nextBucketData: (Pair<LocalDate, D>) -> Pair<LocalDate, D> = { (previousDate, previousData) ->
        val nextDate = when (interval.granularity) {
            TimeGranularity.DAILY -> previousDate + DatePeriod(days = 1)
            TimeGranularity.MONTHLY -> previousDate + DatePeriod(months = 1)
            TimeGranularity.YEARLY -> previousDate + DatePeriod(years = 1)
        }
        nextDate to nextFunction(nextDate, previousData)
    }
    return generateSequence(firstBucketData, nextBucketData)
        .takeWhile { (date, _) -> date <= interval.interval.to }
        .toList()
}

@Serializable
data class GranularDateInterval(
    val interval: DateInterval,
    val granularity: TimeGranularity,
) {
    fun getTimeBuckets(): List<LocalDate> {
        val firstBucket = getTimeBucket(interval.from, granularity)
        val nextBucket = { previous: LocalDate ->
            when (granularity) {
                TimeGranularity.DAILY -> previous + DatePeriod(days = 1)
                TimeGranularity.MONTHLY -> previous + DatePeriod(months = 1)
                TimeGranularity.YEARLY -> previous + DatePeriod(years = 1)
            }
        }
        return generateSequence(firstBucket, nextBucket)
            .takeWhile { it <= interval.to }
            .toList()
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
