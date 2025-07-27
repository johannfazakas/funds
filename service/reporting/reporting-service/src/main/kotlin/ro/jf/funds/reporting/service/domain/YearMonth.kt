package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

@Serializable
data class YearMonth(
    val year: Int,
    val month: Int,
) : Comparable<YearMonth> {
    override fun compareTo(other: YearMonth): Int =
        LocalDate(year, month, 1).compareTo(LocalDate(other.year, other.month, 1))

    val startDate by lazy { LocalDate(year, month, 1) }
    val endDate by lazy { LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY) }

    fun asTimeBucket() = TimeBucket(from = startDate, to = endDate)

    fun next(): YearMonth {
        val nextMonth = if (month == 12) 1 else month + 1
        val nextYear = if (nextMonth == 1) year + 1 else year
        return YearMonth(nextYear, nextMonth)
    }
}
