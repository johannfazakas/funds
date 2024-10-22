package ro.jf.funds.reporting.api.model

import kotlinx.datetime.LocalDateTime

data class GranularTimeIntervalTO(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val granularity: DataGranularityTO,
)
