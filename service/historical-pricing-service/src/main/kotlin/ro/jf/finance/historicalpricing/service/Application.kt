package ro.jf.finance.historicalpricing.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.finance.historicalpricing.service.config.configureDependencyInjection
import ro.jf.finance.historicalpricing.service.config.configureRouting
import ro.jf.finance.historicalpricing.service.config.configureSerialization
import ro.jf.finance.historicalpricing.service.domain.service.currency.CurrencyService
import ro.jf.finance.historicalpricing.service.domain.service.instrument.InstrumentService

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencyInjection()
    configureSerialization()

    configureRouting(
        instrumentService = get<InstrumentService>(),
        currencyService = get<CurrencyService>()
    )
}
