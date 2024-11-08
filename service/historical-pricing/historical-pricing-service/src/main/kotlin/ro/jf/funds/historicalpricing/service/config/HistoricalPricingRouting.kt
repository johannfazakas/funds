package ro.jf.funds.historicalpricing.service.config

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ro.jf.funds.historicalpricing.api.model.CurrencyConversionRequest
import ro.jf.funds.historicalpricing.api.model.InstrumentConversionRequest
import ro.jf.funds.historicalpricing.service.domain.service.currency.CurrencyService

fun Application.configureRouting(
    instrumentService: ro.jf.funds.historicalpricing.service.domain.service.instrument.InstrumentService,
    currencyService: CurrencyService,
) {
    routing {
        route("/api/historical-pricing") {
            post("/instruments/convert") {
                call.respond(instrumentService.convert(call.receive<InstrumentConversionRequest>()))
            }
            post("/currencies/convert") {
                call.respond(currencyService.convert(call.receive<CurrencyConversionRequest>()))
            }
        }
    }
}
