package ro.jf.funds.reporting.service.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.commons.api.model.ListTO
import ro.jf.funds.commons.web.userId
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTO
import ro.jf.funds.reporting.api.model.TimeGranularityTO
import ro.jf.funds.reporting.api.model.YearMonthTO
import ro.jf.funds.reporting.service.domain.ReportDataInterval
import ro.jf.funds.reporting.service.domain.ReportDataInterval.*
import ro.jf.funds.reporting.service.domain.ReportingException
import ro.jf.funds.reporting.service.domain.YearMonth
import ro.jf.funds.reporting.service.service.ReportViewService
import ro.jf.funds.reporting.service.service.reportdata.ReportDataService
import ro.jf.funds.reporting.service.web.mapper.*
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

        get("/{reportViewId}/data/net") {
            val userId = call.userId()
            val reportViewId = call.reportViewId()
            val interval = call.reportDataInterval()
            log.info { "Get net reportdata request for user $userId and report view $reportViewId in interval $interval." }
            val reportData = reportDataService.getNetReport(userId, reportViewId, interval).toTO { it.toNetReportTO() }
            call.respond(status = HttpStatusCode.OK, message = reportData)
        }

        get("/{reportViewId}/data/grouped-net") {
            val userId = call.userId()
            val reportViewId = call.reportViewId()
            val interval = call.reportDataInterval()
            log.info { "Get grouped net reportdata request for user $userId and report view $reportViewId in interval $interval." }
            val reportData =
                reportDataService.getGroupedNetReport(userId, reportViewId, interval).toTO { it.toGroupedNetTO() }
            call.respond(status = HttpStatusCode.OK, message = reportData)
        }

        get("/{reportViewId}/data/value") {
            val userId = call.userId()
            val reportViewId = call.reportViewId()
            val interval = call.reportDataInterval()
            log.info { "Get value reportdata request for user $userId and report view $reportViewId in interval $interval." }
            val reportData =
                reportDataService.getValueReport(userId, reportViewId, interval).toTO { it.toValueReportTO() }
            call.respond(status = HttpStatusCode.OK, message = reportData)
        }

        get("/{reportViewId}/data/grouped-budget") {
            val userId = call.userId()
            val reportViewId = call.reportViewId()
            val interval = call.reportDataInterval()
            log.info { "Get grouped budget reportdata request for user $userId and report view $reportViewId in interval $interval." }
            val reportData =
                reportDataService.getGroupedBudgetReport(userId, reportViewId, interval).toTO { it.toGroupedBudgetTO() }
            call.respond(status = HttpStatusCode.OK, message = reportData)
        }

        get("/{reportViewId}/data/performance") {
            val userId = call.userId()
            val reportViewId = call.reportViewId()
            val interval = call.reportDataInterval()
            log.info { "Get performance reportdata request for user $userId and report view $reportViewId in interval $interval." }
            val reportData =
                reportDataService.getPerformanceReport(userId, reportViewId, interval).toTO { it.toPerformanceTO() }
            call.respond(status = HttpStatusCode.OK, message = reportData)
        }

        get("/{reportViewId}/data/unit-performance") {
            val userId = call.userId()
            val reportViewId = call.reportViewId()
            val interval = call.reportDataInterval()
            log.info { "Get unit performance reportdata request for user $userId and report view $reportViewId in interval $interval." }
            val reportData = reportDataService.getInstrumentPerformanceReport(userId, reportViewId, interval)
                .toTO { it.toInstrumentsPerformanceReportTO() }
            call.respond(status = HttpStatusCode.OK, message = reportData)
        }

        get("/{reportViewId}/data/interest-rate") {
            val userId = call.userId()
            val reportViewId = call.reportViewId()
            val interval = call.reportDataInterval()
            log.info { "Get interest rate reportdata request for user $userId and report view $reportViewId in interval $interval." }
            val reportData =
                reportDataService.getInterestRateReport(userId, reportViewId, interval).toTO { it.toInterestRateTO() }
            call.respond(status = HttpStatusCode.OK, message = reportData)
        }

        get("/{reportViewId}/data/unit-interest-rate") {
            val userId = call.userId()
            val reportViewId = call.reportViewId()
            val interval = call.reportDataInterval()
            log.info { "Get unit interest rate reportdata request for user $userId and report view $reportViewId in interval $interval." }
            val reportData = reportDataService.getInstrumentInterestRateReport(userId, reportViewId, interval)
                .toTO { it.toInstrumentsInterestRateTO() }
            call.respond(status = HttpStatusCode.OK, message = reportData)
        }
    }
}

private fun ApplicationCall.reportDataInterval(): ReportDataInterval {
    val granularity = parameters["granularity"]
        ?.let { TimeGranularityTO.fromString(it) }
        ?: throw ReportingException.MissingGranularity()
    return when (granularity) {
        TimeGranularityTO.YEARLY -> Yearly(
            fromYear = parameters["fromYear"]?.toInt()
                ?: throw ReportingException.MissingIntervalStart(),
            toYear = parameters["toYear"]?.toInt()
                ?: throw ReportingException.MissingIntervalEnd(),
            forecastUntilYear = parameters["forecastUntilYear"]?.toInt()
        )

        TimeGranularityTO.MONTHLY -> Monthly(
            fromYearMonth = parameters["fromYearMonth"]?.parseYearMonth()
                ?: throw ReportingException.MissingIntervalStart(),
            toYearMonth = parameters["toYearMonth"]?.parseYearMonth()
                ?: throw ReportingException.MissingIntervalEnd(),
            forecastUntilYearMonth = parameters["forecastUntilYearMonth"]?.parseYearMonth()
        )

        TimeGranularityTO.DAILY -> Daily(
            fromDate = parameters["fromDate"]?.let(LocalDate::parse)
                ?: throw ReportingException.MissingIntervalStart(),
            toDate = parameters["toDate"]?.let(LocalDate::parse)
                ?: throw ReportingException.MissingIntervalEnd(),
            forecastUntilDate = parameters["forecastUntilDate"]?.let(LocalDate::parse)
        )
    }
}

private fun ApplicationCall.reportViewId(): UUID =
    this.parameters["reportViewId"]?.let(UUID::fromString) ?: error("Missing reportViewId path parameter")

private fun String.parseYearMonth(): YearMonth =
    YearMonthTO.parse(this)
        .let { yearMonth -> YearMonth(year = yearMonth.year, month = yearMonth.month) }
