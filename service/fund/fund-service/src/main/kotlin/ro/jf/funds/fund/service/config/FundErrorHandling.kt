package ro.jf.funds.fund.service.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.error.ErrorTO


private val logger = logger { }

fun Application.configureFundErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unexpected error on ${call.request.httpMethod} ${call.request.path()}" }
            call.respond(HttpStatusCode.InternalServerError, ErrorTO.internal(cause))
        }
    }
}

