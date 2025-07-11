package ro.jf.funds.importer.service.domain

import java.util.*

data class ImportTask(
    val taskId: UUID,
    val userId: UUID,
    val parts: List<ImportTaskPart>,
) {
    fun findPartByName(name: String): ImportTaskPart? =
        parts.firstOrNull { part -> part.name.equals(name, ignoreCase = true) }
}

data class ImportTaskPart(
    val taskPartId: UUID,
    val name: String,
    val status: ImportTaskPartStatus,
    val reason: String? = null,
)

enum class ImportTaskPartStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
