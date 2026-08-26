package ro.jf.funds.analytics.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class ReportIntervalTO(
    val granularity: TimeGranularity,
    val from: LocalDateTime,
    val to: LocalDateTime,
) {
    init {
        require(from < to) { "Report interval start must be before its end" }
    }
}
