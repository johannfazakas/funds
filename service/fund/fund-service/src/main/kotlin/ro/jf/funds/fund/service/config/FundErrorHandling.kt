package ro.jf.funds.fund.service.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.error.ErrorTO

import ro.jf.funds.fund.service.domain.FundServiceException


private val logger = logger { }

fun Application.configureFundErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is FundServiceException -> {
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

fun FundServiceException.toStatusCode(): HttpStatusCode = when (this) {
    is FundServiceException.FundNotFound -> HttpStatusCode.NotFound
    is FundServiceException.TransactionFundNotFound -> HttpStatusCode.UnprocessableEntity
    is FundServiceException.AccountNameAlreadyExists -> HttpStatusCode.Conflict
    is FundServiceException.AccountNotFound -> HttpStatusCode.NotFound
    is FundServiceException.AccountNameNotFound -> HttpStatusCode.NotFound
    is FundServiceException.RecordAccountNotFound -> HttpStatusCode.UnprocessableEntity
    is FundServiceException.RecordFundNotFound -> HttpStatusCode.UnprocessableEntity
    is FundServiceException.AccountRecordCurrencyMismatch -> HttpStatusCode.UnprocessableEntity
}

fun Throwable.toError(): ErrorTO {
    return when (this) {
        is FundServiceException -> this.toError()
        else -> ErrorTO.internal(this)
    }
}

fun FundServiceException.toError(): ErrorTO {
    return when (this) {
        is FundServiceException.FundNotFound -> ErrorTO(
            title = "Fund not found",
            detail = "Fund with id '${fundId}' not found"
        )

        is FundServiceException.TransactionFundNotFound -> ErrorTO(
            title = "Transaction fund not found",
            detail = "Transaction fund with id '${fundId}' not found"
        )

        is FundServiceException.AccountNameAlreadyExists -> ErrorTO(
            title = "Account name already exists",
            detail = "Account with name '${accountName.value}' already exists"
        )

        is FundServiceException.AccountNotFound -> ErrorTO(
            title = "Account not found",
            detail = "Account with id '$accountId' not found"
        )

        is FundServiceException.AccountNameNotFound -> ErrorTO(
            title = "Account name not found",
            detail = "Account with name '${accountName.value}' not found"
        )

        is FundServiceException.RecordAccountNotFound -> ErrorTO(
            title = "Record account not found",
            detail = "Account with id '$accountId' not found for record"
        )

        is FundServiceException.RecordFundNotFound -> ErrorTO(
            title = "Record fund not found",
            detail = "Fund with id '$fundId' not found for record"
        )

        is FundServiceException.AccountRecordCurrencyMismatch -> ErrorTO(
            title = "Account record currency mismatch",
            detail = "Account ${accountName.value} with id $accountId has currency ${accountUnit.value} but record has currency ${recordUnit.value}"
        )
    }
}
