package ro.jf.funds.importer.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging.logger
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.commons.web.toApiException
import ro.jf.funds.importer.api.ImportApi
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportTaskTO
import java.io.File
import java.util.*

private const val LOCALHOST_BASE_URL = "http://localhost:5207"

private val log = logger { }

class ImportSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : ImportApi {
    override suspend fun import(
        userId: UUID,
        importConfiguration: ImportConfigurationTO,
        csvFile: File,
    ): ImportTaskTO {
        return import(userId, importConfiguration, listOf(csvFile))
    }

    override suspend fun import(
        userId: UUID,
        importConfiguration: ImportConfigurationTO,
        csvFiles: List<File>,
    ): ImportTaskTO = withSuspendingSpan {
        log.info { "Importing CSV files ${csvFiles.map { it.name }} for user $userId." }
        val response: HttpResponse = httpClient.post("$baseUrl/funds-api/import/v1/imports/tasks") {
            header(USER_ID_HEADER, userId.toString())
            setBody(
                MultiPartFormDataContent(
                    formData {
                        csvFiles.forEachIndexed { index, csvFile ->
                            append("file-$index", csvFile.readBytes(), Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Text.CSV)
                                append(HttpHeaders.ContentDisposition, "filename=\"${csvFile.name}\"")
                            })
                        }
                        append("configuration", Json.encodeToString(importConfiguration), Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.Json)
                        })
                    }
                ))
        }
        when (response.status) {
            HttpStatusCode.Accepted -> response.body<ImportTaskTO>()
            else -> throw response.toApiException()
        }
    }

    override suspend fun getImportTask(userId: UUID, taskId: UUID): ImportTaskTO = withSuspendingSpan {
        log.info { "Getting import task $taskId for user $userId." }
        val response: HttpResponse = httpClient.get("$baseUrl/funds-api/import/v1/imports/tasks/$taskId") {
            header(USER_ID_HEADER, userId.toString())
        }
        when (response.status) {
            HttpStatusCode.OK -> response.body<ImportTaskTO>()
            else -> throw response.toApiException()
        }
    }
}
