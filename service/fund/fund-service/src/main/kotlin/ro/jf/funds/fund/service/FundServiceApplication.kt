package ro.jf.funds.fund.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.config.configureTracing
import ro.jf.funds.fund.service.config.configureFundErrorHandling
import ro.jf.funds.fund.service.config.configureFundEventHandling
import ro.jf.funds.fund.service.config.configureFundRouting
import ro.jf.funds.fund.service.config.fundDependencies
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencies(fundDependencies)
    configureContentNegotiation()
    configureFundErrorHandling()
    configureDatabaseMigration(get<DataSource>())
    configureFundRouting()
    configureFundEventHandling()
    configureTracing()
}
