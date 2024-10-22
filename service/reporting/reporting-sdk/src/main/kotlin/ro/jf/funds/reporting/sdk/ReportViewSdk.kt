package ro.jf.funds.reporting.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.sdk.client.createHttpClient
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.reporting.api.ReportViewApi
import ro.jf.funds.reporting.api.exception.ReportingApiException
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.CreateReportViewTaskTO
import ro.jf.funds.reporting.api.model.ReportViewTO
import java.util.*

private const val LOCALHOST_BASE_URL = "http://localhost:5212"

private val log = logger { }

class ReportViewSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : ReportViewApi {
    override suspend fun createReportView(userId: UUID, request: CreateReportViewTO): CreateReportViewTaskTO {
        log.info { "Creating for user $userId report view $request." }
        val response = httpClient.post("$baseUrl/bk-api/reporting/v1/report-views") {
            header(USER_ID_HEADER, userId.toString())
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.Accepted) {
            log.warn { "Unexpected response on create fund: $response" }
            throw ReportingApiException.Generic()
        }
        return response.body()
    }

    override suspend fun getReportViewTask(userId: UUID, taskId: UUID): CreateReportViewTaskTO {
        TODO("Not yet implemented")
    }

    override suspend fun getReportView(userId: UUID, reportId: UUID): ReportViewTO {
        TODO("Not yet implemented")
    }

    override suspend fun listReportsViews(userId: UUID): ListTO<ReportViewTO> {
        TODO("Not yet implemented")
    }
}