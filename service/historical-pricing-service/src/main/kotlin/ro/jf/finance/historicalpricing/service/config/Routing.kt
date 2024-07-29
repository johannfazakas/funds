package ro.jf.finance.historicalpricing.service.config

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ro.jf.bk.historicalpricing.api.model.CurrencyConversionRequest
import ro.jf.bk.historicalpricing.api.model.InstrumentConversionRequest
import ro.jf.finance.historicalpricing.service.domain.service.currency.CurrencyService
import ro.jf.finance.historicalpricing.service.domain.service.instrument.InstrumentService

fun Application.configureRouting(
    instrumentService: InstrumentService,
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
