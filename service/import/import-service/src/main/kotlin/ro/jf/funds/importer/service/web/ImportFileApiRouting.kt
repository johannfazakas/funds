package ro.jf.funds.importer.service.web

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.importer.api.model.*
import ro.jf.funds.importer.service.domain.ImportFile
import ro.jf.funds.importer.service.domain.ImportFileStatus
import ro.jf.funds.importer.service.domain.CreateImportFileResponse
import ro.jf.funds.importer.service.service.ImportFileService
import ro.jf.funds.platform.jvm.web.userId
import java.util.*

private val log = logger { }

fun Routing.importFileApiRouting(
    importFileService: ImportFileService,
) {
    route("/funds-api/import/v1/import-files") {
        post {
            val userId = call.userId()
            val request = call.receive<CreateImportFileRequestTO>()
            log.info { "Create import file for user $userId, file ${request.fileName}." }
            val response = importFileService.createImportFile(userId, request.fileName, request.type)
            call.respond(HttpStatusCode.Created, response.toTO())
        }

        post("/{importFileId}/confirm-upload") {
            val userId = call.userId()
            val importFileId = UUID.fromString(call.parameters["importFileId"])
            log.info { "Confirm upload for import file $importFileId, user $userId." }
            val importFile = importFileService.confirmUpload(userId, importFileId)
            if (importFile != null) {
                call.respond(HttpStatusCode.OK, importFile.toTO())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get {
            val userId = call.userId()
            log.info { "List import files for user $userId." }
            val files = importFileService.listImportFiles(userId)
            call.respond(HttpStatusCode.OK, files.map { it.toTO() })
        }

        get("/{importFileId}") {
            val userId = call.userId()
            val importFileId = UUID.fromString(call.parameters["importFileId"])
            log.info { "Get import file $importFileId for user $userId." }
            val importFile = importFileService.getImportFile(userId, importFileId)
            if (importFile != null) {
                call.respond(HttpStatusCode.OK, importFile.toTO())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get("/{importFileId}/download") {
            val userId = call.userId()
            val importFileId = UUID.fromString(call.parameters["importFileId"])
            log.info { "Download URL for import file $importFileId, user $userId." }
            val downloadUrl = importFileService.generateDownloadUrl(userId, importFileId)
            if (downloadUrl != null) {
                call.respond(HttpStatusCode.OK, DownloadUrlResponseTO(downloadUrl = downloadUrl))
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

private fun CreateImportFileResponse.toTO() = CreateImportFileResponseTO(
    importFileId = importFile.importFileId,
    fileName = importFile.fileName,
    type = importFile.type,
    status = importFile.status.toTO(),
    uploadUrl = uploadUrl,
)

private fun ImportFile.toTO() = ImportFileTO(
    importFileId = importFileId,
    fileName = fileName,
    type = type,
    status = status.toTO(),
)

private fun ImportFileStatus.toTO() = when (this) {
    ImportFileStatus.PENDING -> ImportFileStatusTO.PENDING
    ImportFileStatus.UPLOADED -> ImportFileStatusTO.UPLOADED
}
