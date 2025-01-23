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
import ro.jf.funds.reporting.api.ReportingApi
import ro.jf.funds.reporting.api.model.*
import java.util.*

private const val LOCALHOST_BASE_URL = "http://localhost:5212"

private val log = logger { }

class ReportingSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : ReportingApi {
    override suspend fun createReportView(userId: UUID, request: CreateReportViewTO): ReportViewTaskTO {
        log.info { "Creating for user $userId report view $request." }
        val response = httpClient.post("$baseUrl/funds-api/reporting/v1/report-views/tasks") {
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
        val response = httpClient.get("$baseUrl/funds-api/reporting/v1/report-views/tasks/$taskId") {
            header(USER_ID_HEADER, userId.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on get report view task: $response" }
            throw response.toApiException()
        }
        return response.body()
    }

    override suspend fun getReportView(userId: UUID, reportViewId: UUID): ReportViewTO {
        log.info { "Getting report view for user $userId and report $reportViewId." }
        val response = httpClient.get("$baseUrl/funds-api/reporting/v1/report-views/$reportViewId") {
            header(USER_ID_HEADER, userId.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on get report view: $response" }
            throw response.toApiException()
        }
        return response.body()
    }

    override suspend fun listReportViews(userId: UUID): ListTO<ReportViewTO> {
        log.info { "Listing report views for user $userId." }
        val response = httpClient.get("$baseUrl/funds-api/reporting/v1/report-views") {
            header(USER_ID_HEADER, userId.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list report views: $response" }
            throw response.toApiException()
        }
        return response.body()
    }

    override suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        granularInterval: GranularDateInterval,
    ): ReportDataTO {
        log.info { "Getting report view data for user $userId and report $reportViewId in interval $granularInterval." }
        val response = httpClient.get("$baseUrl/funds-api/reporting/v1/report-views/$reportViewId/data") {
            header(USER_ID_HEADER, userId.toString())
            parameter("from", granularInterval.interval.from.toString())
            parameter("to", granularInterval.interval.to.toString())
            parameter("granularity", granularInterval.granularity.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on get report view data: $response" }
            throw response.toApiException()
        }
        return response.body()
    }
}