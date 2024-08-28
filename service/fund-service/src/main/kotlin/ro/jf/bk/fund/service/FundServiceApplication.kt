package ro.jf.bk.fund.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.bk.commons.service.config.configureContentNegotiation
import ro.jf.bk.commons.service.config.configureDatabaseMigration
import ro.jf.bk.commons.service.config.configureDependencies
import ro.jf.bk.fund.service.config.configureRouting
import ro.jf.bk.fund.service.config.fundsAppModule
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencies(fundsAppModule)
    configureContentNegotiation()
    configureDatabaseMigration(get<DataSource>())
    configureRouting()
}
