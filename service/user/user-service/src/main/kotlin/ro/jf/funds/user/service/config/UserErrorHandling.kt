package ro.jf.funds.user.service.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.error.ErrorTO
import ro.jf.funds.user.service.domain.UserException

private val logger = logger { }

fun Application.configureUserErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is UserException -> {
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

fun UserException.toStatusCode(): HttpStatusCode = when (this) {
    is UserException.UserNotFound -> HttpStatusCode.NotFound
}

fun Throwable.toError(): ErrorTO {
    return when (this) {
        is UserException -> this.toError()
        else -> ErrorTO.internal(this)
    }
}

fun UserException.toError(): ErrorTO {
    return when (this) {
        is UserException.UserNotFound -> ErrorTO(
            title = "User not found",
            detail = "User with id '$userId' not found"
        )
    }
}
