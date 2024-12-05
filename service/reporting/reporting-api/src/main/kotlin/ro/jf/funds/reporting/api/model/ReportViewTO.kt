package ro.jf.funds.reporting.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class ReportViewTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    @Serializable(with = UUIDSerializer::class)
    val fundId: UUID,
    val type: ReportViewType,
)
