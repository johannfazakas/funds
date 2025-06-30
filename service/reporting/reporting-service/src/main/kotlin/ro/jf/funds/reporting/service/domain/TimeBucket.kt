package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate

data class TimeBucket(
    val from: LocalDate,
    val to: LocalDate,
) {
    init {
        require(from <= to) { "From date must be before or equal to the to date. from = $from, to = $to." }
    }
}
