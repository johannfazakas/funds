package ro.jf.funds.reporting.api.model

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.api.serialization.UuidSerializer

@Serializable
data class ReportViewTO(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    val name: String,
    @Serializable(with = UuidSerializer::class)
    val fundId: Uuid,
    val dataConfiguration: ReportDataConfigurationTO,
)
