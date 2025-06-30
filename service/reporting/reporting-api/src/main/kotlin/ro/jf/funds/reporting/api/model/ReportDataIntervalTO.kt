package ro.jf.funds.reporting.api.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

@Serializable
data class ReportDataIntervalTO(
    val granularity: TimeGranularityTO,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val forecastUntilDate: LocalDate? = null,
)

@Serializable
data class DateIntervalTO(
    val from: LocalDate,
    val to: LocalDate,
) {
    init {
        require(from <= to) { "From date must be before or equal to the to date. from = $from, to = $to." }
    }

    constructor(from: YearMonthTO, to: YearMonthTO) : this(
        LocalDate(from.year, from.month, 1),
        LocalDate(to.year, to.month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY),
    )
}

@Serializable
data class YearMonthTO(
    val year: Int,
    val month: Int,
) : Comparable<YearMonthTO> {
    override fun compareTo(other: YearMonthTO): Int =
        LocalDate(year, month, 1).compareTo(LocalDate(other.year, other.month, 1))

    fun asDateInterval(): DateIntervalTO = DateIntervalTO(
        from = LocalDate(year, month, 1),
        to = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY),
    )

    fun next(): YearMonthTO {
        val nextMonth = if (month == 12) 1 else month + 1
        val nextYear = if (nextMonth == 1) year + 1 else year
        return YearMonthTO(nextYear, nextMonth)
    }
}
