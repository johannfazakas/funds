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
