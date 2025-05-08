package ro.jf.funds.historicalpricing.service.config

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ro.jf.funds.historicalpricing.api.model.CurrencyConversionRequest
import ro.jf.funds.historicalpricing.api.model.InstrumentConversionRequest
import ro.jf.funds.historicalpricing.service.service.ConversionService
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyService
import ro.jf.funds.historicalpricing.service.service.instrument.InstrumentService
import ro.jf.funds.historicalpricing.service.web.historicalPricingApiRouting

fun Application.configureHistoricalPricingRouting(
    conversionService: ConversionService,
    instrumentService: InstrumentService,
    currencyService: CurrencyService,
) {
    routing {
        historicalPricingApiRouting(conversionService)
    }
}
