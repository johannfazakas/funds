package ro.jf.funds.account.service.web

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.api.model.PropertyTO
import ro.jf.funds.account.api.model.AccountTransactionFilterTO
import ro.jf.funds.account.service.domain.AccountTransaction
import ro.jf.funds.account.service.service.AccountTransactionService
import ro.jf.funds.account.service.web.mapper.toTO
import ro.jf.funds.commons.model.toListTO
import ro.jf.funds.commons.web.userId
import java.util.*

private val log = logger { }

private const val RECORD_PROPERTIES_PREFIX = "properties.record."
private const val TRANSACTION_PROPERTIES_PREFIX = "properties.transaction."

fun Routing.accountTransactionApiRouting(transactionService: AccountTransactionService) {
    route("/funds-api/account/v1/transactions") {
        post {
            val userId = call.userId()
            val request = call.receive<CreateAccountTransactionTO>()
            log.debug { "Create transaction $request for user $userId." }
            val transaction = transactionService.createTransaction(userId, request)
            call.respond(HttpStatusCode.Created, transaction.toTO())
        }
        post("/batch") {
            val userId = call.userId()
            val request = call.receive<CreateAccountTransactionsTO>()
            log.debug { "Create ${request.transactions.size} transactions for user $userId." }
            val transactions = transactionService.createTransactions(userId, request)
            call.respond(transactions.toListTO(AccountTransaction::toTO))
        }
        get {
            val userId = call.userId()
            log.debug { "List all transactions by user id $userId." }
            val transactionFilter = call.parameters.transactionFilter()
            val transactions = transactionService.listTransactions(userId, transactionFilter)
            call.respond(transactions.toListTO(AccountTransaction::toTO))
        }
        delete("/{transactionId}") {
            val userId = call.userId()
            val transactionId =
                call.parameters["transactionId"]?.let(UUID::fromString) ?: error("Transaction id is missing.")
            log.debug { "Delete transaction by user id $userId and transaction id $transactionId." }
            transactionService.deleteTransaction(userId, transactionId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun Parameters.transactionFilter(): AccountTransactionFilterTO {
    val fromDate = this["fromDate"]?.let { LocalDate.parse(it) }
    val toDate = this["toDate"]?.let { LocalDate.parse(it) }
    val recordProperties = this.entries()
        .map { (key, value) -> key to value }
        .filter { (key, _) -> key.startsWith(RECORD_PROPERTIES_PREFIX) }
        .flatMap { (key, values) -> values.map { PropertyTO(key.removePrefix(RECORD_PROPERTIES_PREFIX) to it) } }
    val transactionProperties = this.entries()
        .map { (key, value) -> key to value }
        .filter { (key, _) -> key.startsWith(TRANSACTION_PROPERTIES_PREFIX) }
        .flatMap { (key, values) -> values.map { PropertyTO(key.removePrefix(TRANSACTION_PROPERTIES_PREFIX) to it) } }
    return AccountTransactionFilterTO(fromDate, toDate, transactionProperties, recordProperties)
}