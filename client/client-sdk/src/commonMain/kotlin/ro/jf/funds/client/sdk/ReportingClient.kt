package ro.jf.funds.client.sdk

import co.touchlab.kermit.Logger
import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import ro.jf.funds.platform.api.model.ListTO
import ro.jf.funds.reporting.api.model.*

private const val LOCALHOST_BASE_URL = "http://localhost:5212"
private const val BASE_PATH = "/funds-api/reporting/v1"

class ReportingClient(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val log = Logger.withTag("ReportingClient")

    suspend fun listReportViews(userId: Uuid): List<ReportViewTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/report-views") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.w { "Unexpected response on list report views: $response" }
            throw Exception("Failed to list report views: ${response.status}")
        }
        val data = response.body<ListTO<ReportViewTO>>()
        log.d { "Retrieved report views: $data" }
        return data.items
    }

    suspend fun getGroupedBudgetData(
        userId: Uuid,
        reportViewId: Uuid,
        interval: ReportDataIntervalTO,
    ): ReportDataTO<ByGroupTO<GroupedBudgetReportTO>> {
        val response = httpClient.get("$baseUrl$BASE_PATH/report-views/$reportViewId/data/grouped-budget") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            applyIntervalParameters(interval)
        }
        if (response.status != HttpStatusCode.OK) {
            log.w { "Unexpected response on get grouped budget data: $response" }
            throw Exception("Failed to get grouped budget data: ${response.status}")
        }
        val data = response.body<ReportDataTO<ByGroupTO<GroupedBudgetReportTO>>>()
        log.d { "Retrieved grouped budget data: $data" }
        return data
    }

    private fun HttpRequestBuilder.applyIntervalParameters(interval: ReportDataIntervalTO) {
        parameter("granularity", interval.granularity.name)
        when (interval) {
            is ReportDataIntervalTO.Daily -> {
                parameter("fromDate", interval.fromDate)
                parameter("toDate", interval.toDate)
                interval.forecastUntilDate?.let { parameter("forecastUntilDate", it) }
            }
            is ReportDataIntervalTO.Monthly -> {
                parameter("fromYearMonth", interval.fromYearMonth)
                parameter("toYearMonth", interval.toYearMonth)
                interval.forecastUntilYearMonth?.let { parameter("forecastUntilYearMonth", it) }
            }
            is ReportDataIntervalTO.Yearly -> {
                parameter("fromYear", interval.fromYear)
                parameter("toYear", interval.toYear)
                interval.forecastUntilYear?.let { parameter("forecastUntilYear", it) }
            }
        }
    }
}
