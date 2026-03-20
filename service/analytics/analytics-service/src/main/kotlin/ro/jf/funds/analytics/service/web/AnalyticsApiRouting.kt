package ro.jf.funds.analytics.service.web

import com.benasher44.uuid.Uuid
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.AnalyticsValueReportRequestTO
import ro.jf.funds.analytics.service.domain.AnalyticsRecordFilter
import ro.jf.funds.analytics.service.service.AnalyticsRecordService
import ro.jf.funds.platform.jvm.web.userId

private val log = logger { }

fun Routing.analyticsApiRouting(analyticsRecordService: AnalyticsRecordService) {
    route("/funds-api/analytics/v1/records") {
        post("/value-report") {
            val userId = Uuid.fromString(call.userId().toString())
            val request = call.receive<AnalyticsValueReportRequestTO>()
            log.info { "Value report request for user $userId: $request" }
            val filter = AnalyticsRecordFilter(
                fundIds = request.fundIds,
                units = request.units,
            )
            val report = analyticsRecordService.getValueReport(
                userId = userId,
                granularity = request.granularity,
                from = request.from,
                to = request.to,
                filter = filter,
            )
            call.respond(HttpStatusCode.OK, report)
        }
    }
}
