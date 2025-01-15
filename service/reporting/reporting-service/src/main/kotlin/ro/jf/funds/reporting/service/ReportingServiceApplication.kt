package ro.jf.funds.reporting.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.reporting.service.config.configureReportingErrorHandling
import ro.jf.funds.reporting.service.config.configureReportingEventHandling
import ro.jf.funds.reporting.service.config.reportingDependencies
import ro.jf.funds.reporting.service.config.configureReportingRouting
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencies(reportingDependencies)
    configureReportingErrorHandling()
    configureContentNegotiation()
    configureDatabaseMigration(get<DataSource>())
    configureReportingEventHandling()
    configureReportingRouting()
}
