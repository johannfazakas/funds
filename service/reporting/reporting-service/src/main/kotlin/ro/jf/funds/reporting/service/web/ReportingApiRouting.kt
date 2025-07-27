package ro.jf.funds.reporting.service.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.web.userId
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTO
import ro.jf.funds.reporting.api.model.TimeGranularityTO
import ro.jf.funds.reporting.api.serializer.YearMonthSerializer
import ro.jf.funds.reporting.service.domain.ReportDataInterval
import ro.jf.funds.reporting.service.domain.ReportingException
import ro.jf.funds.reporting.service.domain.YearMonth
import ro.jf.funds.reporting.service.service.ReportViewService
import ro.jf.funds.reporting.service.service.data.ReportDataService
import ro.jf.funds.reporting.service.web.mapper.toDomain
import ro.jf.funds.reporting.service.web.mapper.toTO
import java.util.*

private val log = logger { }

fun Routing.reportingApiRouting(
    reportViewService: ReportViewService,
    reportDataService: ReportDataService,
) {
    route("/funds-api/reporting/v1/report-views") {
        post {
            val userId = call.userId()
            val request = call.receive<CreateReportViewTO>()
            log.info { "Create report view request for user $userId: $request" }
            val response = reportViewService.createReportView(userId, request.toDomain(userId)).toTO()
            call.respond(status = HttpStatusCode.Created, message = response)
        }

        get {
            val userId = call.userId()
            log.info { "List report views request for user $userId." }
            val response = reportViewService.listReportViews(userId)
                .map { it.toTO() }.let(::ListTO)
            call.respond(status = HttpStatusCode.OK, message = response)
        }

        get("/{reportViewId}") {
            val userId = call.userId()
            val reportViewId =
                call.parameters["reportViewId"]?.let(UUID::fromString)
                    ?: error("Missing reportViewId path parameter")
            log.info { "Get report view request for user $userId and report view $reportViewId." }
            val response: ReportViewTO = reportViewService.getReportView(userId, reportViewId).toTO()
            call.respond(status = HttpStatusCode.OK, message = response)
        }

        get("/{reportViewId}/data") {
            val userId = call.userId()
            val reportViewId =
                call.parameters["reportViewId"]?.let(UUID::fromString)
                    ?: error("Missing reportViewId path parameter")
            val interval = call.reportDataInterval()
            log.info { "Get report data request for user $userId and report view $reportViewId in interval $interval." }
            val reportData = reportDataService.getReportViewData(userId, reportViewId, interval).toTO()
            call.respond(status = HttpStatusCode.OK, message = reportData)

        }
    }
}

private fun ApplicationCall.reportDataInterval(): ReportDataInterval {
    val granularity = parameters["granularity"]
        ?.let { TimeGranularityTO.fromString(it) }
        ?: throw ReportingException.MissingGranularity()
    return when (granularity) {
        TimeGranularityTO.YEARLY -> yearlyReportDataInterval()
        TimeGranularityTO.MONTHLY -> monthlyReportDataInterval()
        TimeGranularityTO.DAILY -> dailyReportDataInterval()
    }
}

private fun ApplicationCall.yearlyReportDataInterval(): ReportDataInterval.Yearly =
    ReportDataInterval.Yearly(
        fromYear = parameters["fromYear"]?.toInt()
            ?: throw ReportingException.MissingIntervalStart(),
        toYear = parameters["toYear"]?.toInt()
            ?: throw ReportingException.MissingIntervalEnd(),
        forecastUntilYear = parameters["forecastUntilYear"]?.toInt()
    )

private fun ApplicationCall.monthlyReportDataInterval(): ReportDataInterval.Monthly {
    return ReportDataInterval.Monthly(
        fromYearMonth = parameters["fromYearMonth"]?.parseYearMonth()
            ?: throw ReportingException.MissingIntervalStart(),
        toYearMonth = parameters["toYearMonth"]?.parseYearMonth()
            ?: throw ReportingException.MissingIntervalEnd(),
        forecastUntilYearMonth = parameters["forecastUntilYearMonth"]?.parseYearMonth()
    )
}

private fun ApplicationCall.dailyReportDataInterval(): ReportDataInterval.Daily {
    return ReportDataInterval.Daily(
        fromDate = parameters["fromDate"]?.let(LocalDate::parse)
            ?: throw ReportingException.MissingIntervalStart(),
        toDate = parameters["toDate"]?.let(LocalDate::parse)
            ?: throw ReportingException.MissingIntervalEnd(),
        forecastUntilDate = parameters["forecastUntil"]?.let(LocalDate::parse)
    )
}

private fun String.parseYearMonth(): YearMonth =
    Json.decodeFromString(YearMonthSerializer(), this)
        .let { yearMonth -> YearMonth(year = yearMonth.year, month = yearMonth.month) }

