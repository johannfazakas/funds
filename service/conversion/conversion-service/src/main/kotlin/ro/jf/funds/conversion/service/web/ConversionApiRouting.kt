package ro.jf.funds.conversion.service.web

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.service.service.ConversionService

private val log = logger { }

fun Routing.conversionApiRouting(
    conversionService: ConversionService,
) {
    post("/funds-api/conversion/v1/conversions") {
        val request = call.receive<ConversionsRequest>()
        log.info { "Received conversion request $request" }
        val response = conversionService.convert(request)
        call.respond(HttpStatusCode.OK, response)
    }
}
