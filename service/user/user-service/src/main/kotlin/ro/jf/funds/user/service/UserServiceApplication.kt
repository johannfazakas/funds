package ro.jf.funds.user.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.config.configureTracing
import ro.jf.funds.user.service.config.configureUserRouting
import ro.jf.funds.user.service.config.userDependencies
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureTracing()
    configureDependencies(userDependencies)
    configureContentNegotiation()
    configureDatabaseMigration(get<DataSource>())
    configureUserRouting()
}
