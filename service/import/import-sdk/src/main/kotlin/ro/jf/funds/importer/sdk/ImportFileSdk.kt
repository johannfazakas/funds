package ro.jf.funds.importer.sdk

import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.importer.api.model.CreateImportFileRequest
import ro.jf.funds.importer.api.model.CreateImportFileResponseTO
import ro.jf.funds.importer.api.model.ImportFileTO
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import ro.jf.funds.platform.jvm.web.createHttpClient
import ro.jf.funds.platform.jvm.web.toApiException

private const val LOCALHOST_BASE_URL = "http://localhost:5207"

private val log = logger { }

class ImportFileSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
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
