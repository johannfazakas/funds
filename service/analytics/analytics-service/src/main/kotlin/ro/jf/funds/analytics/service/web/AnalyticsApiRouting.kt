package ro.jf.funds.analytics.service.web

import com.benasher44.uuid.Uuid
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.AnalyticsReportRequestTO
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.AnalyticsRecordFilter
import ro.jf.funds.analytics.service.service.AnalyticsService
import ro.jf.funds.platform.jvm.web.userId

private val log = logger { }

fun Routing.analyticsApiRouting(analyticsService: AnalyticsService) {
    route("/funds-api/analytics/v1/reports") {
        post("/balance") {
            val userId = Uuid.fromString(call.userId().toString())
            val request = call.receive<AnalyticsReportRequestTO>()
            log.info { "Balance report request for user $userId: $request" }
            val interval = ReportInterval(request.granularity, request.from, request.to)
            val filter = AnalyticsRecordFilter(fundIds = request.fundIds, units = request.units)
            val report = analyticsService.getBalanceReport(userId, interval, filter, request.targetCurrency)
            call.respond(HttpStatusCode.OK, report)
        }
        post("/net-change") {
            val userId = Uuid.fromString(call.userId().toString())
            val request = call.receive<AnalyticsReportRequestTO>()
            log.info { "Net change report request for user $userId: $request" }
            val interval = ReportInterval(request.granularity, request.from, request.to)
            val filter = AnalyticsRecordFilter(fundIds = request.fundIds, units = request.units)
            val report = analyticsService.getNetChangeReport(userId, interval, filter, request.targetCurrency)
            call.respond(HttpStatusCode.OK, report)
        }
    }
}
