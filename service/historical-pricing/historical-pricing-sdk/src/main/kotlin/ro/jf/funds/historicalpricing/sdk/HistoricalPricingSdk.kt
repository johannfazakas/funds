package ro.jf.funds.historicalpricing.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.commons.web.toApiException
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import java.util.*

private val log = logger { }

private const val LOCALHOST_BASE_URL = "http://localhost:5231"

class HistoricalPricingSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
    suspend fun convert(userId: UUID, request: ConversionsRequest): ConversionsResponse = withSuspendingSpan {
        if (request.conversions.isEmpty()) {
            return@withSuspendingSpan ConversionsResponse.empty()
        }
        val response = httpClient.post("${baseUrl}/funds-api/historical-pricing/v1/conversions") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on conversion: $response" }
            throw response.toApiException()
        }
        response.body()
    }
}
