package ro.jf.funds.reporting.service.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.web.userId
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTO
import ro.jf.funds.reporting.service.service.ReportViewService
import ro.jf.funds.reporting.service.service.ReportViewTaskService
import ro.jf.funds.reporting.service.web.mapper.toTO
import java.util.*

private val log = logger { }

fun Routing.reportingViewApiRouting(
    reportViewService: ReportViewService,
    reportViewTaskService: ReportViewTaskService,
) {
    route("/funds-api/reporting/v1/report-views") {
        post("/tasks") {
            val userId = call.userId()
            val request = call.receive<CreateReportViewTO>()
            log.info { "Create report view request for user $userId: $request" }
            val response = reportViewTaskService
                .triggerReportViewTask(userId, request)
                .toTO { reportViewId -> reportViewService.getReportView(userId, reportViewId) }
            call.respond(status = HttpStatusCode.Accepted, message = response)
        }

        get("/tasks/{reportViewTaskId}") {
            val userId = call.userId()
            val taskId =
                call.parameters["reportViewTaskId"]?.let(UUID::fromString) ?: error("Missing taskId path parameter")
            log.info { "Get report view task request for user $userId and task $taskId." }
            val response = reportViewTaskService.getReportViewTask(userId, taskId)
                ?.toTO { reportViewId -> reportViewService.getReportView(userId, reportViewId) }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(status = HttpStatusCode.OK, message = response)
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
                call.parameters["reportViewId"]?.let(UUID::fromString) ?: error("Missing reportViewId path parameter")
            log.info { "Get report view request for user $userId and report view $reportViewId." }
            val response: ReportViewTO = reportViewService.getReportView(userId, reportViewId).toTO()
            call.respond(status = HttpStatusCode.OK, message = response)
        }

        get("/{reportViewId}/data") {
            val userId = call.userId()
            val reportViewId =
                call.parameters["reportViewId"]?.let(UUID::fromString) ?: error("Missing reportViewId path parameter")
            log.info { "Get report view data request for user $userId and report view $reportViewId." }
        }
    }
}
