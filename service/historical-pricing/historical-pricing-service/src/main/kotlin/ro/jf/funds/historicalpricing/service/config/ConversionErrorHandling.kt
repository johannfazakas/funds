package ro.jf.funds.historicalpricing.service.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.error.ErrorTO
import ro.jf.funds.historicalpricing.service.domain.ConversionExceptions

private val logger = logger { }

fun Application.configureConversionErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is ConversionExceptions -> {
                    logger.warn(cause) { "Application error on ${call.request.httpMethod} ${call.request.path()}" }
                    call.respond(cause.toStatusCode(), cause.toError())
                }

                else -> {
                    logger.error(cause) { "Unexpected error on ${call.request.httpMethod} ${call.request.path()}" }
                    call.respond(HttpStatusCode.InternalServerError, cause.toError())
                }
            }
        }
    }
}

fun ConversionExceptions.toStatusCode(): HttpStatusCode = when (this) {
    is ConversionExceptions.ConversionNotPermitted -> HttpStatusCode.BadRequest
    is ConversionExceptions.ConversionNotFound -> HttpStatusCode.NotFound
    is ConversionExceptions.ConversionIntegrationException -> HttpStatusCode.BadGateway
    is ConversionExceptions.InstrumentSourceIntegrationNotFound -> HttpStatusCode.UnprocessableEntity
}

fun Throwable.toError(): ErrorTO {
    return when (this) {
        is ConversionExceptions -> this.toError()
        else -> ErrorTO.internal(this)
    }
}

fun ConversionExceptions.toError(): ErrorTO {
    return when (this) {
        is ConversionExceptions.ConversionNotPermitted -> ErrorTO(
            title = "Conversion not permitted",
            detail = "Conversion not permitted from ${this.sourceUnit} to ${this.targetUnit}",
        )

        is ConversionExceptions.ConversionNotFound -> ErrorTO(
            title = "Conversion not found",
            detail = "Conversion for ${this.sourceUnit.value} to ${this.targetUnit.value} on ${this.date} not found"
        )

        is ConversionExceptions.ConversionIntegrationException -> ErrorTO(
            title = "Integration error",
            detail = "Error from $api API (status $status): $errorDetail"
        )

        is ConversionExceptions.InstrumentSourceIntegrationNotFound -> ErrorTO(
            title = "Instrument source integration not found",
            detail = "No pricing source integration configured for instrument ${this.instrument.value}"
        )
    }
}