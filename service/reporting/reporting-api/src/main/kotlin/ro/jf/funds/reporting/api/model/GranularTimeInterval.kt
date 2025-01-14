package ro.jf.funds.reporting.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class GranularTimeInterval(
    val start: LocalDate,
    val end: LocalDate,
    val granularity: DataGranularity,
)
