package ro.jf.funds.importer.service.domain

import com.benasher44.uuid.Uuid
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import java.time.LocalDateTime

enum class ImportFileStatus {
    PENDING,
    UPLOADED,
    IMPORTING,
    IMPORTED,
    IMPORT_FAILED,
}

data class ImportFile(
    val importFileId: Uuid,
    val userId: Uuid,
    val fileName: String,
    val type: ImportFileTypeTO,
    val status: ImportFileStatus,
    val importConfigurationId: Uuid,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val errors: List<String> = emptyList(),
) {
    val s3Key: String get() = "$userId/$fileName"
}

data class ImportFileFilter(
    val type: ImportFileTypeTO? = null,
    val status: ImportFileStatus? = null,
    val importConfigurationId: Uuid? = null,
)
