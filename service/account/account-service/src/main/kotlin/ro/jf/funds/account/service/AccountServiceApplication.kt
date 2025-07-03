package ro.jf.funds.account.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.account.service.config.accountDependencyModules
import ro.jf.funds.account.service.config.configureAccountErrorHandling
import ro.jf.funds.account.service.config.configureAccountEventHandling
import ro.jf.funds.account.service.config.configureAccountRouting
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.config.configureTracing
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureTracing()
    configureDependencies(*accountDependencyModules)
    configureAccountErrorHandling()
    configureContentNegotiation()
    configureDatabaseMigration(get<DataSource>())
    configureAccountRouting()
    configureAccountEventHandling()
}
