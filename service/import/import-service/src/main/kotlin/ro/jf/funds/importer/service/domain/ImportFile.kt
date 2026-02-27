package ro.jf.funds.importer.service.domain

import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.platform.jvm.error.ErrorTO
import java.time.LocalDateTime
import java.util.*

enum class ImportFileStatus {
    PENDING,
    UPLOADED,
    IMPORTING,
    IMPORTED,
    IMPORT_FAILED,
}

data class ImportFile(
    val importFileId: UUID,
    val userId: UUID,
    val fileName: String,
    val type: ImportFileTypeTO,
    val status: ImportFileStatus,
    val importConfigurationId: UUID,
    val createdAt: LocalDateTime,
    val errors: List<ErrorTO> = emptyList(),
) {
    val s3Key: String get() = "$userId/$fileName"
}

data class ImportFileFilter(
    val type: ImportFileTypeTO? = null,
    val status: ImportFileStatus? = null,
    val importConfigurationId: UUID? = null,
)
