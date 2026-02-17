package ro.jf.funds.fund.service.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging.logger
import ro.jf.funds.platform.jvm.error.ErrorTO

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
    is FundServiceException.FundNameAlreadyExists -> HttpStatusCode.Conflict
    is FundServiceException.FundHasRecords -> HttpStatusCode.Conflict
    is FundServiceException.TransactionNotFound -> HttpStatusCode.NotFound
    is FundServiceException.TransactionFundNotFound -> HttpStatusCode.UnprocessableEntity
    is FundServiceException.AccountNameAlreadyExists -> HttpStatusCode.Conflict
    is FundServiceException.AccountNotFound -> HttpStatusCode.NotFound
    is FundServiceException.AccountNameNotFound -> HttpStatusCode.NotFound
    is FundServiceException.AccountHasRecords -> HttpStatusCode.Conflict
    is FundServiceException.RecordAccountNotFound -> HttpStatusCode.UnprocessableEntity
    is FundServiceException.RecordFundNotFound -> HttpStatusCode.UnprocessableEntity
    is FundServiceException.AccountRecordCurrencyMismatch -> HttpStatusCode.UnprocessableEntity
    is FundServiceException.LabelNotFound -> HttpStatusCode.NotFound
    is FundServiceException.LabelNameAlreadyExists -> HttpStatusCode.Conflict
    is FundServiceException.LabelHasRecords -> HttpStatusCode.Conflict
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

        is FundServiceException.FundNameAlreadyExists -> ErrorTO(
            title = "Fund name already exists",
            detail = "Fund with name '${fundName.value}' already exists"
        )

        is FundServiceException.FundHasRecords -> ErrorTO(
            title = "Fund has records",
            detail = "Fund with id '$fundId' has transaction records and cannot be deleted"
        )

        is FundServiceException.TransactionNotFound -> ErrorTO(
            title = "Transaction not found",
            detail = "Transaction with id '${transactionId}' not found"
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

        is FundServiceException.AccountHasRecords -> ErrorTO(
            title = "Account has records",
            detail = "Account with id '$accountId' has transaction records and cannot change unit"
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

        is FundServiceException.LabelNotFound -> ErrorTO(
            title = "Label not found",
            detail = "Label with id '$labelId' not found"
        )

        is FundServiceException.LabelNameAlreadyExists -> ErrorTO(
            title = "Label name already exists",
            detail = "Label with name '$labelName' already exists"
        )

        is FundServiceException.LabelHasRecords -> ErrorTO(
            title = "Label has records",
            detail = "Label with id '$labelId' is used on records and cannot be deleted"
        )
    }
}
