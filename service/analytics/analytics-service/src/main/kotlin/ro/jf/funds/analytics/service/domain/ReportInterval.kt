package ro.jf.funds.analytics.service.domain

import kotlinx.datetime.*
import ro.jf.funds.analytics.api.model.TimeGranularity

data class ReportInterval(
    val granularity: TimeGranularity,
    val from: LocalDateTime,
    val to: LocalDateTime,
) {
    fun generateBuckets(): List<LocalDateTime> = buildList {
        add(from)
        var current = advance(truncate(from))
        while (current < to) {
            add(current)
            current = advance(current)
        }
    }

    fun bucketFor(dateTime: LocalDateTime): LocalDateTime {
        val truncated = truncate(dateTime)
        return if (truncated == truncate(from)) from else truncated
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
