package ro.jf.funds.importer.service.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging.logger
import ro.jf.funds.platform.jvm.error.ErrorTO
import ro.jf.funds.importer.service.domain.exception.*


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
    is ImportFileNotFoundException -> HttpStatusCode.NotFound
    is ImportFileNotUploadedException -> HttpStatusCode.Conflict
    is ImportConfigurationNotFoundException -> HttpStatusCode.NotFound
}

fun ImportServiceException.toError(): ErrorTO = when (this) {
    is ImportDataException -> ErrorTO(
        title = "Import data error",
        detail = message ?: "Import data error",
    )
    is ImportFormatException -> ErrorTO(
        title = "Import format error",
        detail = message ?: "Import format error",
    )
    is MissingImportConfigurationException -> ErrorTO(
        title = "Missing import configuration",
        detail = "Missing import configuration",
    )
    is ImportConfigurationValidationException -> ErrorTO(
        title = "Import configuration validation error",
        detail = message ?: "Import configuration validation error",
    )
    is ImportFileNotFoundException -> ErrorTO(
        title = "Import file not found",
        detail = "Import file $importFileId not found",
    )
    is ImportFileNotUploadedException -> ErrorTO(
        title = "Import file not uploaded",
        detail = "Import file $importFileId has not been uploaded to storage",
    )
    is ImportConfigurationNotFoundException -> ErrorTO(
        title = "Import configuration not found",
        detail = "Import configuration $importConfigurationId not found",
    )
}
