package ro.jf.funds.reporting.api.model

import kotlinx.datetime.*
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
        require(from <= to) { "From date must be before or equal to the to date" }
        1..2
        1 to 2
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
)
