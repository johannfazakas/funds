package ro.jf.funds.user.service.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging.logger
import ro.jf.funds.platform.jvm.error.ErrorTO
import ro.jf.funds.user.service.domain.UserServiceException

private val logger = logger { }

fun Application.configureUserErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is UserServiceException -> {
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

fun UserServiceException.toStatusCode(): HttpStatusCode = when (this) {
    is UserServiceException.UserNotFound -> HttpStatusCode.NotFound
    is UserServiceException.UsernameAlreadyExists -> HttpStatusCode.Conflict
}

fun Throwable.toError(): ErrorTO {
    return when (this) {
        is UserServiceException -> this.toError()
        else -> ErrorTO.internal(this)
    }
}

fun UserServiceException.toError(): ErrorTO {
    return when (this) {
        is UserServiceException.UserNotFound -> ErrorTO(
            title = "User not found",
            detail = "User with id '$userId' not found"
        )
        is UserServiceException.UsernameAlreadyExists -> ErrorTO(
            title = "Username already exists",
            detail = "User with username '$username' already exists"
        )
    }
}
