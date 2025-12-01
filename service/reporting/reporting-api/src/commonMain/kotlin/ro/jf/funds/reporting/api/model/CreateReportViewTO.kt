package ro.jf.funds.reporting.api.model

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.api.serialization.UuidSerializer

@Serializable
data class CreateReportViewTO(
    val name: String,
    @Serializable(with = UuidSerializer::class)
    val fundId: Uuid,
    val dataConfiguration: ReportDataConfigurationTO,
)
