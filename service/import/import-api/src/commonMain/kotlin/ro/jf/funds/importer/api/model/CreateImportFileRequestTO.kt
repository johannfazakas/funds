package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateImportFileRequestTO(val fileName: String, val type: ImportFileTypeTO)
