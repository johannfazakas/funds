package ro.jf.funds.fund.service.web

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.platform.api.model.toListTO
import ro.jf.funds.platform.jvm.web.userId
import ro.jf.funds.fund.api.model.CreateTransactionTO
import ro.jf.funds.fund.api.model.TransactionFilterTO
import ro.jf.funds.fund.service.domain.Transaction
import ro.jf.funds.fund.service.mapper.toTO
import ro.jf.funds.fund.service.service.TransactionService
import java.util.*

private val log = logger { }

fun Routing.fundTransactionApiRouting(transactionService: TransactionService) {
    route("/funds-api/fund/v1") {
        route("/transactions") {
            get {
                val userId = call.userId()
                val filter = call.parameters.fundTransactionsFilter()
                val transactions = transactionService.listTransactions(userId, filter)
                call.respond(transactions.toListTO(Transaction::toTO))
            }
            post {
                val userId = call.userId()
                val request = call.receive<CreateTransactionTO>()
                log.debug { "Create for user id $userId transaction $request." }
                val transaction = transactionService.createTransaction(userId, request)
                call.respond(HttpStatusCode.Created, transaction.toTO())
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
}

fun Parameters.fundTransactionsFilter(): TransactionFilterTO =
    TransactionFilterTO(
        fromDate = this["fromDate"]?.let { LocalDate.parse(it) },
        toDate = this["toDate"]?.let { LocalDate.parse(it) },
        fundId = this["fundId"]?.let(UUID::fromString),
        accountId = this["accountId"]?.let(UUID::fromString),
    )
