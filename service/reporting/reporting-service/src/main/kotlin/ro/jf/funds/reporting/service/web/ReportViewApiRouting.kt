package ro.jf.funds.reporting.service.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.web.userId
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.CreateReportViewTaskTO
import ro.jf.funds.reporting.api.model.ReportViewTO
import java.util.UUID.randomUUID

private val log = logger { }

fun Routing.reportingViewApiRouting(
) {
    route("/bk-api/reporting/v1/report-views") {
        post {
            val userId = call.userId()
            val request = call.receive<CreateReportViewTO>()
            log.info { "Create report view request for user $userId: $request" }
            val response: CreateReportViewTaskTO = CreateReportViewTaskTO.Completed(
                taskId = randomUUID(),
                report = ReportViewTO(
                    name = request.name,
                    fundId = request.fundId,
                    type = request.type
                )
            )
            call.respond(status = HttpStatusCode.Accepted, message = response)
        }
    }
}
