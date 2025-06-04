package ro.jf.funds.reporting.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

enum class ReportViewTaskStatus {
    COMPLETED,
    IN_PROGRESS,
    FAILED,
}

@Serializable
data class ReportViewTaskTO(
    @Serializable(with = UUIDSerializer::class)
    val taskId: UUID,
    val status: ReportViewTaskStatus,
    val report: ReportViewTO? = null,
    val reason: String? = null,
) {
    companion object {
        fun inProgress(taskId: UUID): ReportViewTaskTO =
            ReportViewTaskTO(taskId, ReportViewTaskStatus.IN_PROGRESS)

        fun completed(taskId: UUID, report: ReportViewTO): ReportViewTaskTO =
            ReportViewTaskTO(taskId, ReportViewTaskStatus.COMPLETED, report)

        fun failed(taskId: UUID, reason: String): ReportViewTaskTO =
            ReportViewTaskTO(taskId, ReportViewTaskStatus.FAILED, reason = reason)
    }
}
