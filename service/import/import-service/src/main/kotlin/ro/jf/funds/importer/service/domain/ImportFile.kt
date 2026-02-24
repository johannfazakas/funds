package ro.jf.funds.importer.service.domain

import ro.jf.funds.importer.api.model.ImportFileTypeTO
import java.time.LocalDateTime
import java.util.*

enum class ImportFileStatus {
    PENDING,
    UPLOADED
}

data class ImportFile(
    val importFileId: UUID,
    val userId: UUID,
    val fileName: String,
    val type: ImportFileTypeTO,
    val s3Key: String,
    val status: ImportFileStatus,
    val createdAt: LocalDateTime,
)

data class ImportFileFilter(
    val type: ImportFileTypeTO? = null,
    val status: ImportFileStatus? = null,
)
