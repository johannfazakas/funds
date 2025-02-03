package ro.jf.funds.reporting.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class CreateReportViewTO(
    val name: String,
    @Serializable(with = UUIDSerializer::class)
    val fundId: UUID,
    val type: ReportViewType,
    val currency: Currency,
    // TODO(Johann) should this be applied only on certain features or on all features? will figure it out later
    val labels: List<Label>,
)
