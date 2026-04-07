package ro.jf.funds.analytics.api.model

import com.benasher44.uuid.Uuid
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
data class AnalyticsReportRequestTO(
    val granularity: TimeGranularity,
    val from: LocalDateTime,
    val to: LocalDateTime,
    val fundIds: List<@Serializable(with = UuidSerializer::class) Uuid> = emptyList(),
    val units: List<FinancialUnit> = emptyList(),
    val targetCurrency: Currency,
    val groupBy: GroupingCriteria? = null,
)
