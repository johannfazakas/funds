package ro.jf.funds.reporting.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import ro.jf.funds.reporting.service.web.reportingViewApiRouting

fun Application.reportingRouting() {
    routing {
        reportingViewApiRouting()
    }
}