package ro.jf.funds.conversion.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.platform.jvm.config.configureContentNegotiation
import ro.jf.funds.platform.jvm.config.configureDatabaseMigration
import ro.jf.funds.platform.jvm.config.configureDependencies
import ro.jf.funds.platform.jvm.config.configureTracing
import ro.jf.funds.conversion.service.config.configureConversionErrorHandling
import ro.jf.funds.conversion.service.config.configureConversionRouting
import ro.jf.funds.conversion.service.config.conversionDependencies
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
