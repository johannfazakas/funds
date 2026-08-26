package ro.jf.funds.analytics.service.domain

import com.benasher44.uuid.Uuid
import ro.jf.funds.analytics.api.model.GroupingCriteria
import ro.jf.funds.platform.api.model.Currency

data class SeriesResolutionContext(
    val userId: Uuid,
    val interval: ReportInterval,
    val targetCurrency: Currency,
    val grouping: GroupingCriteria? = null,
    val filter: AnalyticsInputRecordFilter = AnalyticsInputRecordFilter(),
)
