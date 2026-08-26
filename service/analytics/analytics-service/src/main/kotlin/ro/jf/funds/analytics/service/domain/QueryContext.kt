package ro.jf.funds.analytics.service.domain

import ro.jf.funds.analytics.api.model.GroupingCriteria

enum class ContextDimension {
    GROUPING,
    FILTER,
    ;

    companion object {
        val ALL: Set<ContextDimension> = entries.toSet()
    }
}

data class QueryContext(
    val grouping: GroupingCriteria? = null,
    val filter: AnalyticsInputRecordFilter = AnalyticsInputRecordFilter(),
) {
    fun projected(sensitivity: Set<ContextDimension>): QueryContext = QueryContext(
        grouping = grouping.takeIf { ContextDimension.GROUPING in sensitivity },
        filter = if (ContextDimension.FILTER in sensitivity) filter else AnalyticsInputRecordFilter(),
    )
}
