package ro.jf.funds.reporting.service.domain

import java.util.*

sealed class ReportViewTask(
    val status: Status,
) {
    abstract val userId: UUID
    abstract val taskId: UUID

    enum class Status {
        COMPLETED,
        IN_PROGRESS,
        FAILED,
        ;

        companion object {
            fun fromString(value: String): Status = entries.single { it.name == value }
        }
    }

    data class InProgress(
        override val userId: UUID,
        override val taskId: UUID,
    ) : ReportViewTask(Status.COMPLETED)

    data class Completed(
        override val userId: UUID,
        override val taskId: UUID,
        val reportViewId: UUID,
    ) : ReportViewTask(Status.COMPLETED)

    data class Failed(
        override val userId: UUID,
        override val taskId: UUID,
        val reason: String,
    ) : ReportViewTask(Status.FAILED)
}
