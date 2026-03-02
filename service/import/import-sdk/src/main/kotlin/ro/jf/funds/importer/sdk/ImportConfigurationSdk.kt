package ro.jf.funds.importer.sdk

import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.importer.api.model.CreateImportConfigurationRequest
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import ro.jf.funds.platform.jvm.web.createHttpClient
import ro.jf.funds.platform.jvm.web.toApiException

private const val LOCALHOST_BASE_URL = "http://localhost:5207"

private val log = logger { }

class ImportConfigurationSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
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
}
