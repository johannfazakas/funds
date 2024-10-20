package ro.jf.bk.account.service.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.bk.account.api.model.CreateAccountTransactionTO
import ro.jf.bk.account.service.web.mapper.toTO
import ro.jf.bk.account.service.domain.AccountTransaction
import ro.jf.bk.account.service.service.AccountTransactionService
import ro.jf.bk.commons.model.toListTO
import ro.jf.bk.commons.service.routing.userId
import java.util.*

private val log = logger { }

fun Routing.accountTransactionApiRouting(transactionService: AccountTransactionService) {
    route("/bk-api/account/v1/transactions") {
        post {
            val userId = call.userId()
            val request = call.receive<CreateAccountTransactionTO>()
            log.debug { "Create transaction $request for user $userId." }
            val transaction = transactionService.createTransaction(userId, request)
            call.respond(HttpStatusCode.Created, transaction.toTO())
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
