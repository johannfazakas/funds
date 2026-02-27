package ro.jf.funds.importer.sdk

import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging.logger
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import ro.jf.funds.platform.jvm.web.createHttpClient
import ro.jf.funds.platform.jvm.web.toApiException
import ro.jf.funds.importer.api.model.*
import ro.jf.funds.platform.api.model.PageTO
import java.io.File

private const val LOCALHOST_BASE_URL = "http://localhost:5207"

private val log = logger { }

class ImportSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
    suspend fun import(
        userId: Uuid,
        fileType: ImportFileTypeTO,
        importConfiguration: ImportConfigurationTO,
        csvFile: File,
    ): ImportTaskTO {
        return import(userId, fileType, importConfiguration, listOf(csvFile))
    }

    suspend fun import(
        userId: Uuid,
        fileType: ImportFileTypeTO,
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
                        append("fileType", fileType.name)
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

    suspend fun getImportTask(userId: Uuid, taskId: Uuid): ImportTaskTO = withSuspendingSpan {
        log.info { "Getting import task $taskId for user $userId." }
        val response: HttpResponse = httpClient.get("$baseUrl/funds-api/import/v1/imports/tasks/$taskId") {
            header(USER_ID_HEADER, userId.toString())
        }
        when (response.status) {
            HttpStatusCode.OK -> response.body<ImportTaskTO>()
            else -> throw response.toApiException()
        }
    }

    suspend fun listImportConfigurations(userId: Uuid): PageTO<ImportConfigurationTO> = withSuspendingSpan {
        log.info { "Listing import configurations for user $userId." }
        val response: HttpResponse = httpClient.get("$baseUrl/funds-api/import/v1/import-configurations") {
            header(USER_ID_HEADER, userId.toString())
        }
        when (response.status) {
            HttpStatusCode.OK -> response.body<PageTO<ImportConfigurationTO>>()
            else -> throw response.toApiException()
        }
    }

    suspend fun createImportConfiguration(
        userId: Uuid,
        request: CreateImportConfigurationRequest,
    ): ImportConfigurationTO = withSuspendingSpan {
        log.info { "Creating import configuration '${request.name}' for user $userId." }
        val response: HttpResponse = httpClient.post("$baseUrl/funds-api/import/v1/import-configurations") {
            header(USER_ID_HEADER, userId.toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        when (response.status) {
            HttpStatusCode.Created -> response.body<ImportConfigurationTO>()
            else -> throw response.toApiException()
        }
    }

    suspend fun createImportFile(
        userId: Uuid,
        request: CreateImportFileRequest,
    ): CreateImportFileResponseTO = withSuspendingSpan {
        log.info { "Creating import file '${request.fileName}' for user $userId." }
        val response: HttpResponse = httpClient.post("$baseUrl/funds-api/import/v1/import-files") {
            header(USER_ID_HEADER, userId.toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        when (response.status) {
            HttpStatusCode.Created -> response.body<CreateImportFileResponseTO>()
            else -> throw response.toApiException()
        }
    }

    suspend fun uploadFile(uploadUrl: String, fileContent: ByteArray): Unit = withSuspendingSpan {
        log.info { "Uploading file to presigned URL." }
        val response: HttpResponse = httpClient.put(uploadUrl) {
            setBody(fileContent)
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to upload file: ${response.status}")
        }
    }

    suspend fun confirmUpload(userId: Uuid, importFileId: Uuid): ImportFileTO = withSuspendingSpan {
        log.info { "Confirming upload for import file $importFileId, user $userId." }
        val response: HttpResponse = httpClient.post("$baseUrl/funds-api/import/v1/import-files/$importFileId/confirm-upload") {
            header(USER_ID_HEADER, userId.toString())
        }
        when (response.status) {
            HttpStatusCode.OK -> response.body<ImportFileTO>()
            else -> throw response.toApiException()
        }
    }

    suspend fun importFile(userId: Uuid, importFileId: Uuid): ImportFileTO = withSuspendingSpan {
        log.info { "Triggering import for file $importFileId, user $userId." }
        val response: HttpResponse = httpClient.post("$baseUrl/funds-api/import/v1/import-files/$importFileId/import") {
            header(USER_ID_HEADER, userId.toString())
        }
        when (response.status) {
            HttpStatusCode.Accepted -> response.body<ImportFileTO>()
            else -> throw response.toApiException()
        }
    }

    suspend fun getImportFile(userId: Uuid, importFileId: Uuid): ImportFileTO = withSuspendingSpan {
        log.info { "Getting import file $importFileId for user $userId." }
        val response: HttpResponse = httpClient.get("$baseUrl/funds-api/import/v1/import-files/$importFileId") {
            header(USER_ID_HEADER, userId.toString())
        }
        when (response.status) {
            HttpStatusCode.OK -> response.body<ImportFileTO>()
            else -> throw response.toApiException()
        }
    }
}
