package ro.jf.funds.importer.service.web

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.ProblemTO
import ro.jf.funds.commons.service.routing.userId
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportResponse
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.domain.exception.ImportException
import ro.jf.funds.importer.service.domain.exception.ImportFormatException
import ro.jf.funds.importer.service.service.ImportService
import ro.jf.funds.importer.service.web.mapper.toProblem

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
                ?: return@post call.respond(HttpStatusCode.BadRequest, ProblemTO("Import configuration missing."))

            try {
                importService.import(userId, importConfiguration, rawFileParts)
                // TODO(Johann) should probably return something relevant from the service
                call.respond(HttpStatusCode.Created, ImportResponse("Imported in service"))
            } catch (importException: ImportException) {
                log.warn(importException) { "Error importing for user $userId." }
                val statusCode = when (importException) {
                    is ImportFormatException -> HttpStatusCode.BadRequest
                    is ImportDataException -> HttpStatusCode.BadRequest
                }
                return@post call.respond(statusCode, importException.toProblem())
            }
        }
    }
}

private fun List<PartData>.importConfigurationPart(): ImportConfigurationTO? {
    return this
        .mapNotNull { it as? PartData.FormItem }
        .firstOrNull { it.name == "configuration" }
        ?.value
        ?.let { json -> Json.decodeFromString<ImportConfigurationTO>(json) }
}

private fun List<PartData>.rawFileParts(): List<String> {
    return this
        .mapNotNull { it as? PartData.FileItem }
        .map { filePart -> String(filePart.streamProvider().readBytes()).also { filePart.dispose() } }
}
