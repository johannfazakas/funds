package ro.jf.funds.reporting.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class CreateReportViewTO(
    val name: String,
    @Serializable(with = UUIDSerializer::class)
    val fundId: UUID,
    val dataConfiguration: ReportDataConfigurationTO,
)
