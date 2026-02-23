package ro.jf.funds.importer.service.domain

data class CreateImportFileResponse(
    val importFile: ImportFile,
    val uploadUrl: String,
)
