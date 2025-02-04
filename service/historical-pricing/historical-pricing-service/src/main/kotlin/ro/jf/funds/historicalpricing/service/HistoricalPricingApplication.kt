package ro.jf.funds.historicalpricing.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.historicalpricing.service.config.configureHistoricalPricingRouting
import ro.jf.funds.historicalpricing.service.config.historicalPricingDependencies
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyService
import ro.jf.funds.historicalpricing.service.service.instrument.InstrumentService
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

// TODO(Johann) adapt service to the patterns in the other services, add tests
fun Application.module() {
    configureDependencies(historicalPricingDependencies)
    configureContentNegotiation()
    configureDatabaseMigration(get<DataSource>())
    configureHistoricalPricingRouting(
        instrumentService = get<InstrumentService>(),
        currencyService = get<CurrencyService>(),
        conversionService = get()
    )
}
