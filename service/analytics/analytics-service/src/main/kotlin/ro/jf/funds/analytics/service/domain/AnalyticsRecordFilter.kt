package ro.jf.funds.analytics.service.domain

import com.benasher44.uuid.Uuid
import ro.jf.funds.platform.api.model.FinancialUnit

data class AnalyticsRecordFilter(
    val fundIds: List<Uuid> = emptyList(),
    val units: List<FinancialUnit> = emptyList(),
)
