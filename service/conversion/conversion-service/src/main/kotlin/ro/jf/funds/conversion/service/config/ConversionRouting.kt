package ro.jf.funds.conversion.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import ro.jf.funds.conversion.service.service.ConversionService
import ro.jf.funds.conversion.service.web.conversionApiRouting

fun Application.configureConversionRouting(
    conversionService: ConversionService,
) {
    routing {
        conversionApiRouting(conversionService)
    }
}
