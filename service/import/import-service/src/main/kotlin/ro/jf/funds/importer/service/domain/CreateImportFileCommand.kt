package ro.jf.funds.importer.service.domain

import ro.jf.funds.importer.api.model.ImportFileTypeTO
import java.util.*

data class CreateImportFileCommand(
    val userId: UUID,
    val fileName: String,
    val type: ImportFileTypeTO,
    val s3Key: String,
    val importConfigurationId: UUID? = null,
)
