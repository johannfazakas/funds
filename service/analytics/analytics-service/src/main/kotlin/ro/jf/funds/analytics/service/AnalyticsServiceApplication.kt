package ro.jf.funds.analytics.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.analytics.service.config.analyticsDependencies
import ro.jf.funds.analytics.service.config.configureAnalyticsEventHandling
import ro.jf.funds.analytics.service.config.configureAnalyticsRouting
import ro.jf.funds.platform.jvm.config.configureContentNegotiation
import ro.jf.funds.platform.jvm.config.configureCors
import ro.jf.funds.platform.jvm.config.configureDatabaseMigration
import ro.jf.funds.platform.jvm.config.configureDependencies
import ro.jf.funds.platform.jvm.config.configureTracing
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureTracing()
    configureDependencies(analyticsDependencies)
    configureContentNegotiation()
    configureCors()
    configureDatabaseMigration(get<DataSource>())
    configureAnalyticsEventHandling()
    configureAnalyticsRouting()
}
