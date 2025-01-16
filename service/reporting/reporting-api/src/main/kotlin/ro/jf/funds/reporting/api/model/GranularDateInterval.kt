package ro.jf.funds.reporting.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class GranularDateInterval(
    val interval: DateInterval,
    val granularity: DataGranularity,
)

@Serializable
data class DateInterval(
    val from: LocalDate,
    val to: LocalDate,
)
