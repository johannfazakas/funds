package ro.jf.funds.importer.service.web

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import mu.KotlinLogging.logger
import ro.jf.funds.platform.jvm.web.userId
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportTaskTO
import ro.jf.funds.importer.service.domain.ImportFile
import ro.jf.funds.importer.service.domain.exception.MissingImportConfigurationException
import ro.jf.funds.importer.service.service.ImportService
import ro.jf.funds.importer.service.web.mapper.toTO
import java.util.*

private val log = logger { }

fun Routing.importApiRouting(
    importService: ImportService,
) {
    route("/funds-api/import/v1/imports") {
        post("/tasks") {
            val userId = call.userId()
            log.info { "Import request for user $userId." }

            val requestParts: List<ImportPart> = call.readMultipartData()

            val importFiles = requestParts.rawFileParts()

            val importConfiguration: ImportConfigurationTO = requestParts.importConfigurationPart()
            val importTask = importService.startImport(userId, importConfiguration, importFiles).toTO()
            val statusCode = when (importTask.status) {
                ImportTaskTO.Status.FAILED -> HttpStatusCode.BadRequest
                else -> HttpStatusCode.Accepted
            }
            call.respond(statusCode, importTask)
        }

        get("/tasks/{importTaskId}") {
            val userId = call.userId()
            val taskId = call.parameters["importTaskId"]?.let(UUID::fromString)
                ?: throw IllegalArgumentException("Missing taskId")
            log.info { "Import status request for user $userId and task $taskId." }

            val importTask = importService.getImport(userId, taskId)?.toTO()
            if (importTask == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(HttpStatusCode.OK, importTask)
            }
        }
    }
}

data class ImportPart(val name: String?, val contentType: ContentType?, val content: String)

private suspend fun ApplicationCall.readMultipartData(): List<ImportPart> = receiveMultipart()
    .asFlow()
    .map { part ->
        when (part) {
            is PartData.FileItem -> {
                with(ByteChannel(autoFlush = true)) {
                    part.provider().copyAndClose(this)
                    ImportPart(
                        name = part.originalFileName ?: error("Missing file name"),
                        contentType = part.contentType ?: ContentType.Application.OctetStream,
                        content = readRemaining().readByteArray().let(::String)
                    )
                }
            }

            is PartData.FormItem -> {
                ImportPart(name = part.name, contentType = part.contentType, content = part.value)
            }

            else -> error("Unsupported part type")
        }
    }
    .toList()

private fun List<ImportPart>.importConfigurationPart(): ImportConfigurationTO = this
    .singleOrNull { it.name == "configuration" && it.contentType == ContentType.Application.Json }
    ?.content
    ?.let { json -> Json.decodeFromString<ImportConfigurationTO>(json) }
    ?: throw MissingImportConfigurationException("Missing import configuration")

private fun List<ImportPart>.rawFileParts(): List<ImportFile> = this
    .filter { it.name != null && it.contentType == ContentType.Text.CSV }
    .mapNotNull { part -> part.name?.let { ImportFile(it, part.content) } }
