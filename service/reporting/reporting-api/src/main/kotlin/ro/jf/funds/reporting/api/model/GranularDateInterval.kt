package ro.jf.funds.reporting.api.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

@Serializable
data class GranularDateInterval(
    val interval: DateInterval,
    val granularity: TimeGranularity,
)

@Serializable
data class DateInterval(
    val from: LocalDate,
    val to: LocalDate,
) {
    init {
        require(from <= to) { "From date must be before or equal to the to date. from = $from, to = $to." }
    }

    constructor(from: YearMonth, to: YearMonth) : this(
        LocalDate(from.year, from.month, 1),
        LocalDate(to.year, to.month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY),
    )
}

@Serializable
data class YearMonth(
    val year: Int,
    val month: Int,
) : Comparable<YearMonth> {
    override fun compareTo(other: YearMonth): Int =
        LocalDate(year, month, 1).compareTo(LocalDate(other.year, other.month, 1))

    fun asDateInterval(): DateInterval = DateInterval(
        from = LocalDate(year, month, 1),
        to = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY),
    )
}
