package ro.jf.funds.reporting.service.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.error.ErrorTO
import ro.jf.funds.reporting.service.domain.ReportingException

private val logger = logger { }


fun Application.configureReportingErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is ReportingException -> {
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

fun ReportingException.toStatusCode(): HttpStatusCode = when (this) {
    is ReportingException.MissingGranularity -> HttpStatusCode.BadRequest
    is ReportingException.MissingIntervalEnd -> HttpStatusCode.BadRequest
    is ReportingException.MissingIntervalStart -> HttpStatusCode.BadRequest
    is ReportingException.ReportViewNotFound -> HttpStatusCode.NotFound
    is ReportingException.ReportViewAlreadyExists -> HttpStatusCode.Conflict
    is ReportingException.ReportRecordConversionRateNotFound -> HttpStatusCode.UnprocessableEntity
}

fun Throwable.toError(): ErrorTO {
    return when (this) {
        is ReportingException -> this.toError()
        else -> ErrorTO.internal(this)
    }
}

fun ReportingException.toError(): ErrorTO {
    return when (this) {
        is ReportingException.MissingGranularity -> ErrorTO(title = "Missing granularity", detail = null)
        is ReportingException.MissingIntervalEnd -> ErrorTO(title = "Missing interval end", detail = null)
        is ReportingException.MissingIntervalStart -> ErrorTO(title = "Missing interval start", detail = null)
        is ReportingException.ReportViewNotFound -> ErrorTO(
            title = "Report view not found",
            detail = "Report view ${this.reportViewId} not found for user ${this.userId}"
        )

        is ReportingException.ReportViewAlreadyExists -> ErrorTO(
            title = "Report view already exists",
            detail = "Report view with name ${this.reportViewName} already exists for user ${this.userId}"
        )

        is ReportingException.ReportRecordConversionRateNotFound -> ErrorTO(
            title = "Conversion rate not found",
            detail = "Conversion rate for record ${this.recordId} not found"
        )
    }
}
