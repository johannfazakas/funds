package ro.jf.funds.importer.service.web.mapper

import ro.jf.funds.importer.api.model.CreateImportFileResponseTO
import ro.jf.funds.importer.api.model.ImportFileErrorTO
import ro.jf.funds.importer.api.model.ImportFileStatusTO
import ro.jf.funds.importer.api.model.ImportFileTO
import ro.jf.funds.importer.service.domain.CreateImportFileResponse
import ro.jf.funds.importer.service.domain.ImportFile
import ro.jf.funds.importer.service.domain.ImportFileStatus
import ro.jf.funds.platform.jvm.error.ErrorTO

fun CreateImportFileResponse.toCreateTO() = CreateImportFileResponseTO(
    importFileId = importFile.importFileId,
    fileName = importFile.fileName,
    type = importFile.type,
    status = importFile.status.toStatusTO(),
    uploadUrl = uploadUrl,
)

fun ImportFile.toTO() = ImportFileTO(
    importFileId = importFileId,
    fileName = fileName,
    type = type,
    status = status.toStatusTO(),
    importConfigurationId = importConfigurationId,
    createdAt = createdAt.toString(),
    errors = errors.map { it.toTO() },
)

fun ErrorTO.toTO() = ImportFileErrorTO(
    title = title,
    detail = detail,
)

fun ImportFileStatus.toStatusTO() = when (this) {
    ImportFileStatus.PENDING -> ImportFileStatusTO.PENDING
    ImportFileStatus.UPLOADED -> ImportFileStatusTO.UPLOADED
    ImportFileStatus.IMPORTING -> ImportFileStatusTO.IMPORTING
    ImportFileStatus.IMPORTED -> ImportFileStatusTO.IMPORTED
    ImportFileStatus.IMPORT_FAILED -> ImportFileStatusTO.IMPORT_FAILED
}
