package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class ImportTaskTO(
    @Serializable(with = UUIDSerializer::class)
    val taskId: UUID,
    val status: Status,
    val reason: String? = null,
) {
    enum class Status {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
