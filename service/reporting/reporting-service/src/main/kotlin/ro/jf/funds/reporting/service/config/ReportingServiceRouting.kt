package ro.jf.funds.reporting.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.funds.reporting.service.service.data.ReportDataService
import ro.jf.funds.reporting.service.service.ReportViewService
import ro.jf.funds.reporting.service.service.ReportViewTaskService
import ro.jf.funds.reporting.service.web.reportingApiRouting

fun Application.configureReportingRouting() {
    routing {
        reportingApiRouting(get<ReportViewService>(), get<ReportViewTaskService>(), get<ReportDataService>())
    }
}
