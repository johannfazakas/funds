package ro.jf.funds.user.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.commons.service.config.configureContentNegotiation
import ro.jf.funds.commons.service.config.configureDependencies
import ro.jf.funds.user.service.config.configureUserRouting
import ro.jf.funds.user.service.config.configureUserMigration
import ro.jf.funds.user.service.config.userDependencies
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencies(userDependencies)
    configureContentNegotiation()
    // TODO(Johann) is this required? how is it done in fund/account service?
    configureUserMigration(get<DataSource>())
    configureUserRouting()
}
