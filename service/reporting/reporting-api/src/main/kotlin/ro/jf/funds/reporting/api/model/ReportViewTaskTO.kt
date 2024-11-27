package ro.jf.funds.reporting.api.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class ReportViewTaskTO {
    abstract val taskId: UUID

    @Serializable
    @SerialName("completed")
    data class Completed(
        @Serializable(with = UUIDSerializer::class)
        override val taskId: UUID,
        val report: ReportViewTO,
    ) : ReportViewTaskTO()

    @Serializable
    @SerialName("in_progress")
    data class InProgress(
        @Serializable(with = UUIDSerializer::class)
        override val taskId: UUID,
    ) : ReportViewTaskTO()

    @Serializable
    @SerialName("failed")
    data class Failed(
        @Serializable(with = UUIDSerializer::class)
        override val taskId: UUID,
        val reason: String,
    ) : ReportViewTaskTO()
}
