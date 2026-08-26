package ro.jf.funds.analytics.service.domain

import com.benasher44.uuid.Uuid
import ro.jf.funds.platform.api.model.Currency

data class MetricQuery(
    val id: QueryId,
    val metric: Series.Metric,
    val context: QueryContext = QueryContext(),
)

data class MetricResolutionRequest(
    val userId: Uuid,
    val interval: ReportInterval,
    val targetCurrency: Currency,
    val queries: List<MetricQuery>,
) {
    init {
        require(queries.isNotEmpty()) { "At least one query must be requested" }
        val duplicateIds = queries.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "Query ids must be unique, duplicated: $duplicateIds" }
    }

    fun resolutionContext(context: QueryContext): SeriesResolutionContext = SeriesResolutionContext(
        userId = userId,
        interval = interval,
        targetCurrency = targetCurrency,
        grouping = context.grouping,
        filter = context.filter,
    )
}
