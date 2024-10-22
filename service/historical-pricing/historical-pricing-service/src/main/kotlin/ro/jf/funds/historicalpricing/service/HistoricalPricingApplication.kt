package ro.jf.funds.historicalpricing.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.historicalpricing.service.config.configureDependencyInjection
import ro.jf.funds.historicalpricing.service.config.configureRouting
import ro.jf.funds.historicalpricing.service.config.configureSerialization
import ro.jf.funds.historicalpricing.service.domain.service.currency.CurrencyService

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencyInjection()
    configureSerialization()

    configureRouting(
        instrumentService = get<ro.jf.funds.historicalpricing.service.domain.service.instrument.InstrumentService>(),
        currencyService = get<CurrencyService>()
    )
}
