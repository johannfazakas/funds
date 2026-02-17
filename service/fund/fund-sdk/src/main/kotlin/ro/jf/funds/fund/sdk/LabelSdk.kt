package ro.jf.funds.fund.sdk

import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import ro.jf.funds.platform.jvm.web.createHttpClient
import ro.jf.funds.platform.jvm.web.toApiException
import ro.jf.funds.fund.api.LabelApi
import ro.jf.funds.fund.api.model.CreateLabelTO
import ro.jf.funds.fund.api.model.LabelTO

private val log = logger { }

class LabelSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : LabelApi {
    override suspend fun listLabels(userId: Uuid): List<LabelTO> = withSuspendingSpan {
        val response = httpClient.get("$baseUrl$BASE_PATH/labels") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list labels: $response" }
            throw response.toApiException()
        }
        response.body()
    }

    override suspend fun createLabel(userId: Uuid, request: CreateLabelTO): LabelTO = withSuspendingSpan {
        val response = httpClient.post("$baseUrl$BASE_PATH/labels") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.Created) {
            log.warn { "Unexpected response on create label: $response" }
            throw response.toApiException()
        }
        response.body()
    }

    override suspend fun deleteLabelById(userId: Uuid, labelId: Uuid) = withSuspendingSpan {
        val response = httpClient.delete("$baseUrl$BASE_PATH/labels/$labelId") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.NoContent) {
            log.warn { "Unexpected response on delete label: $response" }
            throw response.toApiException()
        }
    }
}
