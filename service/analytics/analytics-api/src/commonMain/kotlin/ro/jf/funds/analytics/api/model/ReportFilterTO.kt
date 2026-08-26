package ro.jf.funds.analytics.api.model

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
data class ReportFilterTO(
    val fundIds: List<@Serializable(with = UuidSerializer::class) Uuid> = emptyList(),
    val units: List<FinancialUnit> = emptyList(),
)
