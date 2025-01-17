package ro.jf.funds.reporting.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class GranularDateInterval(
    val interval: DateInterval,
    val granularity: TimeGranularity,
) {
    fun getTimeBuckets(): List<LocalDate> {
        val buckets = mutableListOf<LocalDate>()
//        var current = interval.from
//        while (current <= interval.to) {
//            buckets.add(current)
//            current = when (granularity) {
//                TimeGranularity.DAILY -> current.plusDays(1)
//                TimeGranularity.MONTHLY -> current.plusMonths(1)
//                TimeGranularity.YEARLY -> current.plusYears(1)
//            }
//        }
        return buckets
    }

//    fun getMonthlyBuckets(dateInterval: DateInterval): List<LocalDate> {
//        val firstBucket = LocalDate(dateInterval.from.year, dateInterval.from.monthNumber, 1)
//        val nextBucket = { previous: LocalDate -> previous.plus(DatePeriod(months = 1)) }
//    }
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
