package ro.jf.funds.historicalpricing.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import ro.jf.funds.historicalpricing.service.service.ConversionService
import ro.jf.funds.historicalpricing.service.web.historicalPricingApiRouting

fun Application.configureHistoricalPricingRouting(
    conversionService: ConversionService,
) {
    routing {
        historicalPricingApiRouting(conversionService)
    }
}
