package ro.jf.funds.analytics.service.domain

import kotlinx.datetime.*
import ro.jf.funds.analytics.api.model.TimeGranularity

data class ReportInterval(
    val granularity: TimeGranularity,
    val from: LocalDateTime,
    val to: LocalDateTime,
) {
    fun <D> generateBucketedData(function: (LocalDateTime) -> D): List<Pair<LocalDateTime, D>> =
        generateBuckets().map { it to function(it) }.toList()

    fun <D> generateBucketedData(seed: D, function: (LocalDateTime, D) -> D): List<Pair<LocalDateTime, D>> =
        generateBuckets().fold(emptyList<Pair<LocalDateTime, D>>() to seed) { (acc, previous), dateTime ->
            val data = function(dateTime, previous)
            (acc + (dateTime to data)) to data
        }.first

    private fun generateBuckets(): Sequence<LocalDateTime> = sequence {
        yield(from)
        var current = advance(truncate(from))
        while (current < to) {
            yield(current)
            current = advance(current)
        }
    }

    fun truncate(dateTime: LocalDateTime): LocalDateTime {
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

    private fun advance(dateTime: LocalDateTime): LocalDateTime {
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
