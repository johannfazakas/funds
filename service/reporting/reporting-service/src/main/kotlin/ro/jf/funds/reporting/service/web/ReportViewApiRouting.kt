package ro.jf.funds.reporting.service.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.web.userId
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTaskTO
import ro.jf.funds.reporting.service.service.ReportViewService
import java.util.*

private val log = logger { }

fun Routing.reportingViewApiRouting(
    reportViewService: ReportViewService,
) {
    route("/funds-api/reporting/v1/report-views") {
        post("/tasks") {
            val userId = call.userId()
            val request = call.receive<CreateReportViewTO>()
            log.info { "Create report view request for user $userId: $request" }
            val response: ReportViewTaskTO = reportViewService.createReportViewTask(userId, request)
            call.respond(status = HttpStatusCode.Accepted, message = response)
        }

        get("/tasks/{reportViewTaskId}") {
            val userId = call.userId()
            val taskId =
                call.parameters["reportViewTaskId"]?.let(UUID::fromString) ?: error("Missing taskId path parameter")
            log.info { "Get report view task request for user $userId and task $taskId." }
            val response: ReportViewTaskTO = reportViewService.getReportViewTask(userId, taskId)
            call.respond(status = HttpStatusCode.OK, message = response)
        }

        get("/{reportViewId}") {
            val userId = call.userId()
            val reportViewId =
                call.parameters["reportViewId"]?.let(UUID::fromString) ?: error("Missing reportViewId path parameter")
            log.info { "Get report view request for user $userId and report view $reportViewId." }
            val response: ReportViewTO = reportViewService.getReportView(userId, reportViewId)
            call.respond(status = HttpStatusCode.OK, message = response)
        }
    }
}
