package ro.jf.funds.analytics.sdk

import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.MetricInfoTO
import ro.jf.funds.analytics.api.model.MetricsReportRequestTO
import ro.jf.funds.analytics.api.model.MetricsReportTO
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import ro.jf.funds.platform.jvm.web.createHttpClient
import ro.jf.funds.platform.jvm.web.toApiException

private val log = logger { }

private const val LOCALHOST_BASE_URL = "http://localhost:5219"
private const val METRICS_PATH = "/funds-api/analytics/v1/metrics"

class AnalyticsSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
    suspend fun listMetrics(): List<MetricInfoTO> = withSuspendingSpan {
        val response = httpClient.get("$baseUrl$METRICS_PATH")
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list metrics: $response" }
            throw response.toApiException()
        }
        response.body()
    }

    suspend fun getMetricsReport(userId: Uuid, request: MetricsReportRequestTO): MetricsReportTO =
        withSuspendingSpan {
            val response = httpClient.post("$baseUrl$METRICS_PATH") {
                headers {
                    append(USER_ID_HEADER, userId.toString())
                }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status != HttpStatusCode.OK) {
                log.warn { "Unexpected response on metrics report: $response" }
                throw response.toApiException()
            }
            response.body()
        }
}
