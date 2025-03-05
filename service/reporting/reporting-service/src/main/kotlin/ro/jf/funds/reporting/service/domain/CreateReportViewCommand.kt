package ro.jf.funds.reporting.service.domain

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class CreateReportViewCommand(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val name: String,
    @Serializable(with = UUIDSerializer::class)
    val fundId: UUID,
    val dataConfiguration: ReportDataConfiguration,
)
