package ro.jf.funds.importer.service.domain

import java.util.*

data class UpdateImportTaskPartCommand(
    val userId: UUID,
    val taskPartId: UUID,
    val status: ImportTaskPartStatus,
    val reason: String? = null,
) {
    companion object {
        fun failed(userId: UUID, taskPartId: UUID, reason: String?) =
            UpdateImportTaskPartCommand(userId, taskPartId, ImportTaskPartStatus.FAILED, reason?.take(255))

        fun completed(userId: UUID, taskPartId: UUID) =
            UpdateImportTaskPartCommand(userId, taskPartId, ImportTaskPartStatus.COMPLETED)
    }
}
