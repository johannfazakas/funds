package ro.jf.funds.reporting.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.reporting.service.config.*
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureTracing()
    configureDependencies(reportingDependencies)
    configureReportingErrorHandling()
    configureContentNegotiation()
    configureDatabaseMigration(get<DataSource>())
    configureReportingEventHandling()
    configureReportingRouting()
}
