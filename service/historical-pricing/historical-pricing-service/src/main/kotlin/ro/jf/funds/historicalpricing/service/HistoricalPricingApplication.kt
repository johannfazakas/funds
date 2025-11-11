package ro.jf.funds.historicalpricing.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.config.configureTracing
import ro.jf.funds.historicalpricing.service.config.configureConversionErrorHandling
import ro.jf.funds.historicalpricing.service.config.configureConversionRouting
import ro.jf.funds.historicalpricing.service.config.conversionDependencies
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureTracing()
    configureDependencies(conversionDependencies)
    configureContentNegotiation()
    configureDatabaseMigration(get<DataSource>())
    configureConversionRouting(get())
    configureConversionErrorHandling()
}
