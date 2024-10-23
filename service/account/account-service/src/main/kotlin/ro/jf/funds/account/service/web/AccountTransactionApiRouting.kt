package ro.jf.funds.account.service.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.account.api.exception.AccountApiException
import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.service.domain.AccountTransaction
import ro.jf.funds.account.service.service.AccountTransactionService
import ro.jf.funds.account.service.web.mapper.toTO
import ro.jf.funds.commons.model.toListTO
import ro.jf.funds.commons.service.routing.userId
import java.util.*

private val log = logger { }

fun Routing.accountTransactionApiRouting(transactionService: AccountTransactionService) {
    route("/bk-api/account/v1/transactions") {
        post {
            val userId = call.userId()
            val request = call.receive<CreateAccountTransactionTO>()
            log.debug { "Create transaction $request for user $userId." }
            try {
                val transaction = transactionService.createTransaction(userId, request)
                call.respond(HttpStatusCode.Created, transaction.toTO())
            } catch (exception: AccountApiException.AccountNotFound) {
                log.warn(exception) { "Account not found." }
                call.respond(HttpStatusCode.UnprocessableEntity, exception)
            } catch (exception: AccountApiException.AccountRecordCurrencyMismatch) {
                log.warn(exception) { "Account record currency mismatch." }
                call.respond(HttpStatusCode.UnprocessableEntity, exception)
            }
        }
        post("/batch") {
            val userId = call.userId()
            val request = call.receive<CreateAccountTransactionsTO>()
            log.debug { "Create ${request.transactions.size} transactions for user $userId." }
            try {
                val transactions = transactionService.createTransactions(userId, request)
                call.respond(transactions.toListTO(AccountTransaction::toTO))
            } catch (exception: AccountApiException.AccountNotFound) {
                log.warn(exception) { "Account not found." }
                call.respond(HttpStatusCode.UnprocessableEntity, exception)
            } catch (exception: AccountApiException.AccountRecordCurrencyMismatch) {
                log.warn(exception) { "Account record currency mismatch." }
                call.respond(HttpStatusCode.UnprocessableEntity, exception)
            }
        }
        get {
            val userId = call.userId()
            log.debug { "List all transactions by user id $userId." }
            val transactions = transactionService.listTransactions(userId)
            call.respond(transactions.toListTO(AccountTransaction::toTO))
        }
        delete("/{transactionId}") {
            val userId = call.userId()
            val transactionId =
                call.parameters["transactionId"]?.let(UUID::fromString) ?: error("Account id is missing.")
            log.debug { "Delete transaction by user id $userId and transaction id $transactionId." }
            transactionService.deleteTransaction(userId, transactionId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
