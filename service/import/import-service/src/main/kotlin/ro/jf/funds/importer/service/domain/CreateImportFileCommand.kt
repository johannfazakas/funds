package ro.jf.funds.importer.service.domain

import com.benasher44.uuid.Uuid
import ro.jf.funds.importer.api.model.ImportFileTypeTO

data class CreateImportFileCommand(
    val userId: Uuid,
    val fileName: String,
    val type: ImportFileTypeTO,
    val importConfigurationId: Uuid,
)
