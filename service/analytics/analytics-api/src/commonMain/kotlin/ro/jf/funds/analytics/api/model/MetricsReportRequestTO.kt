package ro.jf.funds.analytics.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.Currency

@Serializable
data class MetricsReportRequestTO(
    val interval: ReportIntervalTO,
    val targetCurrency: Currency,
    val queries: List<MetricQueryTO>,
) {
    init {
        require(queries.isNotEmpty()) { "At least one query must be requested" }
        val duplicateIds = queries.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "Query ids must be unique, duplicated: $duplicateIds" }
    }
}

@Serializable
data class MetricQueryTO(
    val id: String,
    val metric: MetricTO,
    val grouping: GroupingCriteria? = null,
    val filter: ReportFilterTO = ReportFilterTO(),
) {
    init {
        require(id.isNotBlank()) { "Query id must not be blank" }
    }
}
