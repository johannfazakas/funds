package ro.jf.funds.historicalpricing.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.historicalpricing.service.config.configureHistoricalPricingErrorHandling
import ro.jf.funds.historicalpricing.service.config.configureHistoricalPricingRouting
import ro.jf.funds.historicalpricing.service.config.historicalPricingDependencies
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencies(historicalPricingDependencies)
    configureContentNegotiation()
    configureDatabaseMigration(get<DataSource>())
    configureHistoricalPricingRouting(get())
    configureHistoricalPricingErrorHandling()
}
