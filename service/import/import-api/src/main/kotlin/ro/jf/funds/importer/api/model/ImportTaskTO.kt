package ro.jf.funds.importer.api.model

import java.util.*

data class ImportTaskTO(
    val taskId: UUID,
    val status: Status,
    val reason: String? = null
) {
    enum class Status {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
