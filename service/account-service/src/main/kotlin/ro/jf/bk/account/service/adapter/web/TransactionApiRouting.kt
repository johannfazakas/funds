package ro.jf.bk.account.service.adapter.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.bk.account.service.adapter.mapper.toTO
import ro.jf.bk.account.service.domain.model.Transaction
import ro.jf.bk.account.service.domain.port.TransactionService
import ro.jf.bk.commons.model.toListTO
import ro.jf.bk.commons.service.routing.userId
import java.util.*

private val log = logger { }

fun Routing.transactionApiRouting(transactionService: TransactionService) {
    route("/bk-api/account/v1/transactions") {
        get {
            val userId = call.userId()
            log.debug { "List all transactions by user id $userId." }
            val transactions = transactionService.listTransactions(userId)
            call.respond(transactions.toListTO(Transaction::toTO))
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
