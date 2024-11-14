package ro.jf.funds.reporting.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.reporting.service.config.reportingRouting
import ro.jf.funds.reporting.service.config.reportingDependencies

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencies(reportingDependencies)
    configureContentNegotiation()
    reportingRouting()
}
