package ro.jf.funds.importer.api.model

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
data class ImportTaskTO(
    @Serializable(with = UuidSerializer::class)
    val taskId: Uuid,
    val status: Status,
    val reason: String? = null,
) {
    enum class Status {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
