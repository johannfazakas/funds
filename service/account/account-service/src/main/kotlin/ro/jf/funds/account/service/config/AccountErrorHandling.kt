package ro.jf.funds.account.service.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging.logger
import ro.jf.funds.account.service.domain.AccountServiceException
import ro.jf.funds.account.service.domain.AccountServiceException.*
import ro.jf.funds.commons.error.ErrorTO

private val logger = logger { }

fun Application.configureAccountErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is AccountServiceException -> {
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

fun AccountServiceException.toStatusCode(): HttpStatusCode = when (this) {
    is AccountNameAlreadyExists -> HttpStatusCode.Conflict
    is AccountNotFound -> HttpStatusCode.NotFound
    is AccountNameNotFound -> HttpStatusCode.NotFound
    is RecordAccountNotFound -> HttpStatusCode.UnprocessableEntity
    is AccountRecordCurrencyMismatch -> HttpStatusCode.UnprocessableEntity
}

fun Throwable.toError(): ErrorTO {
    return when (this) {
        is AccountServiceException -> (this as AccountServiceException).toError()
        else -> ErrorTO.internal(this)
    }
}

fun AccountServiceException.toError(): ErrorTO {
    return when (this) {
        is AccountNameAlreadyExists -> ErrorTO(
            title = "Account name already exists",
            detail = "Account with name '${accountName.value}' already exists"
        )

        is AccountNotFound -> ErrorTO(
            title = "Account not found",
            detail = "Account with id '$accountId' not found"
        )

        is AccountNameNotFound -> ErrorTO(
            title = "Account name not found",
            detail = "Account with name '${accountName.value}' not found"
        )

        is RecordAccountNotFound -> ErrorTO(
            title = "Record account not found",
            detail = "Account with id '$accountId' not found for record"
        )

        is AccountRecordCurrencyMismatch -> ErrorTO(
            title = "Account record currency mismatch",
            detail = "Account ${accountName.value} with id $accountId has currency ${accountUnit.value} but record has currency ${recordUnit.value}"
        )
    }
}
