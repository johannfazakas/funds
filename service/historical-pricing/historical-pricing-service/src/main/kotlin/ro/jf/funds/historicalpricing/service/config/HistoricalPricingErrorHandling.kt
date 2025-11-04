package ro.jf.funds.historicalpricing.service.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.error.ErrorTO
import ro.jf.funds.historicalpricing.service.domain.HistoricalPricingExceptions

private val logger = logger { }

fun Application.configureHistoricalPricingErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is HistoricalPricingExceptions -> {
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

fun HistoricalPricingExceptions.toStatusCode(): HttpStatusCode = when (this) {
    is HistoricalPricingExceptions.ConversionNotPermitted -> HttpStatusCode.BadRequest
    is HistoricalPricingExceptions.HistoricalPriceNotFound -> HttpStatusCode.NotFound
    is HistoricalPricingExceptions.HistoricalPricingIntegrationException -> HttpStatusCode.BadGateway
    is HistoricalPricingExceptions.InstrumentSourceIntegrationNotFound -> HttpStatusCode.UnprocessableEntity
}

fun Throwable.toError(): ErrorTO {
    return when (this) {
        is HistoricalPricingExceptions -> this.toError()
        else -> ErrorTO.internal(this)
    }
}

fun HistoricalPricingExceptions.toError(): ErrorTO {
    return when (this) {
        is HistoricalPricingExceptions.ConversionNotPermitted -> ErrorTO(
            title = "Conversion not permitted",
            detail = "Conversion not permitted from ${this.sourceUnit} to ${this.targetUnit}",
        )

        is HistoricalPricingExceptions.HistoricalPriceNotFound -> ErrorTO(
            title = "Historical price not found",
            detail = "Historical price for ${this.sourceUnit.value} to ${this.targetUnit.value} on ${this.date} not found"
        )

        is HistoricalPricingExceptions.HistoricalPricingIntegrationException -> ErrorTO(
            title = "Integration error",
            detail = "Error from $api API (status $status): $errorDetail"
        )

        is HistoricalPricingExceptions.InstrumentSourceIntegrationNotFound -> ErrorTO(
            title = "Instrument source integration not found",
            detail = "No pricing source integration configured for instrument ${this.instrument.value}"
        )
    }
}