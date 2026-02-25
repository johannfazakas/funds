package ro.jf.funds.importer.service.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging.logger
import ro.jf.funds.platform.jvm.error.ErrorTO
import ro.jf.funds.importer.service.domain.exception.ImportConfigurationValidationException
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.domain.exception.ImportFormatException
import ro.jf.funds.importer.service.domain.exception.ImportServiceException
import ro.jf.funds.importer.service.domain.exception.MissingImportConfigurationException


private val logger = logger { }

fun Application.configureImportErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is ImportServiceException -> {
                    logger.warn(cause) { "Application error on ${call.request.httpMethod} ${call.request.path()}" }
                    call.respond(cause.toStatusCode(), cause.toError())
                }

                else -> {
                    logger.error(cause) { "Unexpected error on ${call.request.httpMethod} ${call.request.path()}" }
                    call.respond(HttpStatusCode.InternalServerError, ErrorTO.internal(cause))
                }
            }
        }
    }
}

fun ImportServiceException.toStatusCode(): HttpStatusCode = when (this) {
    is ImportDataException -> HttpStatusCode.UnprocessableEntity
    is ImportFormatException -> HttpStatusCode.BadRequest
    is MissingImportConfigurationException -> HttpStatusCode.BadRequest
    is ImportConfigurationValidationException -> HttpStatusCode.BadRequest
}

fun ImportServiceException.toError(): ErrorTO {
    return when (this) {
        is ImportDataException -> ErrorTO(
            title = "Import reportdata error",
            detail = message ?: "Import reportdata error",
        )

        is ImportFormatException -> ErrorTO(
            title = "Import format error",
            detail = message ?: "Import format error",
        )

        is MissingImportConfigurationException -> ErrorTO(
            title = "Missing import configuration",
            detail = message ?: "Missing import configuration",
        )

        is ImportConfigurationValidationException -> ErrorTO(
            title = "Import configuration validation error",
            detail = message ?: "Import configuration validation error",
        )
    }
}
