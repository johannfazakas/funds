package ro.jf.funds.historicalpricing.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.commons.service.config.configureContentNegotiation
import ro.jf.funds.commons.service.config.configureDependencies
import ro.jf.funds.historicalpricing.service.config.configureRouting
import ro.jf.funds.historicalpricing.service.config.historicalPricingDependencies
import ro.jf.funds.historicalpricing.service.domain.service.currency.CurrencyService

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencies(historicalPricingDependencies)
    // TODO(Johann) how about db migration?
    configureContentNegotiation()
    configureRouting(
        instrumentService = get<ro.jf.funds.historicalpricing.service.domain.service.instrument.InstrumentService>(),
        currencyService = get<CurrencyService>()
    )
}
