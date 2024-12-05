package ro.jf.funds.reporting.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.commons.web.toApiException
import ro.jf.funds.reporting.api.ReportViewApi
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTaskTO
import java.util.*

private const val LOCALHOST_BASE_URL = "http://localhost:5212"

private val log = logger { }

class ReportViewSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : ReportViewApi {
    override suspend fun createReportView(userId: UUID, request: CreateReportViewTO): ReportViewTaskTO {
        log.info { "Creating for user $userId report view $request." }
        val response = httpClient.post("$baseUrl/bk-api/reporting/v1/report-views/tasks") {
            header(USER_ID_HEADER, userId.toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.Accepted) {
            log.warn { "Unexpected response on create fund: $response" }
            throw response.toApiException()
        }
        return response.body()
    }

    override suspend fun getReportViewTask(userId: UUID, taskId: UUID): ReportViewTaskTO {
        log.info { "Getting report view task for user $userId and task $taskId." }
        val response = httpClient.get("$baseUrl/bk-api/reporting/v1/report-views/tasks/$taskId") {
            header(USER_ID_HEADER, userId.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on get report view task: $response" }
            throw response.toApiException()
        }
        return response.body()
    }

    override suspend fun getReportView(userId: UUID, reportId: UUID): ReportViewTO {
        log.info { "Getting report view for user $userId and report $reportId." }
        val response = httpClient.get("$baseUrl/bk-api/reporting/v1/report-views/$reportId") {
            header(USER_ID_HEADER, userId.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on get report view: $response" }
            throw response.toApiException()
        }
        return response.body()
    }

    override suspend fun listReportsViews(userId: UUID): ListTO<ReportViewTO> {
        log.info { "Listing report views for user $userId." }
        val response = httpClient.get("$baseUrl/bk-api/reporting/v1/report-views") {
            header(USER_ID_HEADER, userId.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list report views: $response" }
            throw response.toApiException()
        }
        return response.body()
    }
}