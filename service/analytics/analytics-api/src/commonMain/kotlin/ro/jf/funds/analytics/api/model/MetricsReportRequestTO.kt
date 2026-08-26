package ro.jf.funds.analytics.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.Currency

@Serializable
data class MetricsReportRequestTO(
    val metrics: List<MetricTO>,
    val interval: ReportIntervalTO,
    val filter: ReportFilterTO = ReportFilterTO(),
    val targetCurrency: Currency,
    val grouping: GroupingCriteria? = null,
) {
    init {
        require(metrics.isNotEmpty()) { "At least one metric must be requested" }
    }
}
