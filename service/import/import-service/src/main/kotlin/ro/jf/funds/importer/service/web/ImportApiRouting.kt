package ro.jf.funds.importer.service.web

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging.logger
import ro.jf.funds.commons.web.userId
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportTaskTO
import ro.jf.funds.importer.service.domain.exception.MissingImportConfigurationException
import ro.jf.funds.importer.service.service.ImportService
import java.util.*

private val log = logger { }

fun Routing.importApiRouting(
    importService: ImportService
) {
    route("/bk-api/import/v1/imports") {
        post {
            val userId = call.userId()
            log.info { "Import request for user $userId." }

            val requestParts = call
                .receiveMultipart()
                .readAllParts()

            val rawFileParts = requestParts.rawFileParts()

            val importConfiguration: ImportConfigurationTO = requestParts.importConfigurationPart()
            val importTask = importService.startImport(userId, importConfiguration, rawFileParts)
            val statusCode = when (importTask.status) {
                ImportTaskTO.Status.FAILED -> HttpStatusCode.BadRequest
                else -> HttpStatusCode.Accepted
            }
            call.respond(statusCode, importTask)
        }
    }

    route("/bk-api/import/v1/imports/{importTaskId}") {
        get {
            val userId = call.userId()
            val taskId = call.parameters["taskId"]?.let(UUID::fromString)
                ?: throw IllegalArgumentException("Missing taskId")
            log.info { "Import status request for user $userId and task $taskId." }

            val importTask = importService.getImport(userId, taskId)
            if (importTask == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(HttpStatusCode.OK, importTask)
            }
        }
    }


}

private fun List<PartData>.importConfigurationPart(): ImportConfigurationTO {
    return this
        .mapNotNull { it as? PartData.FormItem }
        .firstOrNull { it.name == "configuration" }
        ?.value
        ?.let { json -> Json.decodeFromString<ImportConfigurationTO>(json) }
        ?: throw MissingImportConfigurationException("Missing import configuration")
}

private fun List<PartData>.rawFileParts(): List<String> {
    return this
        .mapNotNull { it as? PartData.FileItem }
        .map { filePart -> String(filePart.streamProvider().readBytes()).also { filePart.dispose() } }
}
