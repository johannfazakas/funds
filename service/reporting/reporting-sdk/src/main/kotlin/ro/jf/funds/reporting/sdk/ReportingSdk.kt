package ro.jf.funds.reporting.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
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
    override suspend fun createReportView(userId: UUID, request: CreateReportViewTO): ReportViewTO =
        withSuspendingSpan {
            log.info { "Creating for user $userId report view $request." }
            val response = httpClient.post("$baseUrl/funds-api/reporting/v1/report-views") {
                header(USER_ID_HEADER, userId.toString())
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status != HttpStatusCode.Created) {
                log.warn { "Unexpected response on create fund: $response" }
                throw response.toApiException()
            }
            response.body()
        }

    override suspend fun getReportView(userId: UUID, reportViewId: UUID): ReportViewTO = withSuspendingSpan {
        log.info { "Getting report view for user $userId and report $reportViewId." }
        val response = httpClient.get("$baseUrl/funds-api/reporting/v1/report-views/$reportViewId") {
            header(USER_ID_HEADER, userId.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on get report view: $response" }
            throw response.toApiException()
        }
        response.body()
    }

    override suspend fun listReportViews(userId: UUID): ListTO<ReportViewTO> = withSuspendingSpan {
        log.info { "Listing report views for user $userId." }
        val response = httpClient.get("$baseUrl/funds-api/reporting/v1/report-views") {
            header(USER_ID_HEADER, userId.toString())
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list report views: $response" }
            throw response.toApiException()
        }
        response.body()
    }

    // TODO(Johann) remove this endpoint
    @Deprecated("Will have to be replaced by specific endpoints")
    override suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<ReportDataAggregateTO> = withSuspendingSpan {
        log.info { "Get report view data. userId = $userId, reportViewId = $reportViewId, reportDataInterval = $reportDataInterval" }
        getData(userId, reportDataInterval, "$baseUrl/funds-api/reporting/v1/report-views/$reportViewId/data")
    }

    override suspend fun getNetData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<ReportDataNetItemTO> = withSuspendingSpan {
        log.info { "Get net data. userId = $userId, reportViewId = $reportViewId, reportDataInterval = $reportDataInterval" }
        getData(userId, reportDataInterval, "$baseUrl/funds-api/reporting/v1/report-views/$reportViewId/data/net")
    }

    override suspend fun getGroupedNetData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<List<ReportDataGroupedNetItemTO>> = withSuspendingSpan {
        log.info { "Get grouped net data. userId = $userId, reportViewId = $reportViewId, reportDataInterval = $reportDataInterval" }
        getData(
            userId,
            reportDataInterval,
            "$baseUrl/funds-api/reporting/v1/report-views/$reportViewId/data/grouped-net"
        )
    }

    override suspend fun getValueData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<ValueReportItemTO> = withSuspendingSpan {
        log.info { "Get value data. userId = $userId, reportViewId = $reportViewId, reportDataInterval = $reportDataInterval" }
        getData(userId, reportDataInterval, "$baseUrl/funds-api/reporting/v1/report-views/$reportViewId/data/value")
    }

    override suspend fun getGroupedBudgetData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<List<ReportDataGroupedBudgetItemTO>> = withSuspendingSpan {
        log.info { "Get grouped budget data. userId = $userId, reportViewId = $reportViewId, reportDataInterval = $reportDataInterval" }
        getData(
            userId,
            reportDataInterval,
            "$baseUrl/funds-api/reporting/v1/report-views/$reportViewId/data/grouped-budget"
        )
    }

    override suspend fun getPerformanceData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<PerformanceReportTO> = withSuspendingSpan {
        log.info { "Get performance data. userId = $userId, reportViewId = $reportViewId, reportDataInterval = $reportDataInterval" }
        getData(
            userId,
            reportDataInterval,
            "$baseUrl/funds-api/reporting/v1/report-views/$reportViewId/data/performance"
        )
    }

    private suspend inline fun <reified T> getData(
        userId: UUID,
        reportDataInterval: ReportDataIntervalTO,
        urlString: String,
    ): ReportDataTO<T> {
        val response = httpClient.get(urlString) {
            header(USER_ID_HEADER, userId.toString())
            dataIntervalParameters(reportDataInterval)
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on get net data: $response" }
            throw response.toApiException()
        }
        return response.body()
    }

    private fun HttpRequestBuilder.dataIntervalParameters(reportDataInterval: ReportDataIntervalTO) {
        parameter("granularity", reportDataInterval.granularity.name)
        when (reportDataInterval) {
            is ReportDataIntervalTO.Daily -> {
                parameter("fromDate", reportDataInterval.fromDate.toString())
                parameter("toDate", reportDataInterval.toDate.toString())
                reportDataInterval.forecastUntilDate?.let { parameter("forecastUntilDate", it.toString()) }
            }

            is ReportDataIntervalTO.Monthly -> {
                parameter("fromYearMonth", reportDataInterval.fromYearMonth.toString())
                parameter("toYearMonth", reportDataInterval.toYearMonth.toString())
                reportDataInterval.forecastUntilYearMonth?.let {
                    parameter("forecastUntilYearMonth", it.toString())
                }
            }

            is ReportDataIntervalTO.Yearly -> {
                parameter("fromYear", reportDataInterval.fromYear.toString())
                parameter("toYear", reportDataInterval.toYear.toString())
                reportDataInterval.forecastUntilYear?.let { parameter("forecastUntilYear", it.toString()) }
            }
        }
    }
}
