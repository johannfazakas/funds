package ro.jf.funds.user.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.platform.jvm.config.configureContentNegotiation
import ro.jf.funds.platform.jvm.config.configureCors
import ro.jf.funds.platform.jvm.config.configureDatabaseMigration
import ro.jf.funds.platform.jvm.config.configureDependencies
import ro.jf.funds.platform.jvm.config.configureTracing
import ro.jf.funds.user.service.config.configureUserErrorHandling
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
    configureCors()
    configureUserErrorHandling()
    configureDatabaseMigration(get<DataSource>())
    configureUserRouting()
}
