package ro.jf.funds.fund.service.web

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.toListTO
import ro.jf.funds.commons.web.userId
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.FundTransactionFilterTO
import ro.jf.funds.fund.service.domain.FundTransaction
import ro.jf.funds.fund.service.mapper.toTO
import ro.jf.funds.fund.service.service.FundTransactionService
import java.util.*

private val log = logger { }

fun Routing.fundTransactionApiRouting(fundTransactionService: FundTransactionService) {
    route("/funds-api/fund/v1") {
        route("/transactions") {
            get {
                val userId = call.userId()
                log.debug { "List all transactions by user id $userId." }
                val filter = call.parameters.fundTransactionsFilter()
                val fundTransactions = fundTransactionService.listTransactions(userId, filter)
                call.respond(fundTransactions.toListTO(FundTransaction::toTO))
            }
            post {
                val userId = call.userId()
                val request = call.receive<CreateFundTransactionTO>()
                log.debug { "Create for user id $userId transaction $request." }
                val transaction = fundTransactionService.createTransaction(userId, request)
                call.respond(HttpStatusCode.Created, transaction.toTO())
            }
            delete("/{transactionId}") {
                val userId = call.userId()
                val transactionId =
                    call.parameters["transactionId"]?.let(UUID::fromString) ?: error("Transaction id is missing.")
                log.debug { "Delete transaction by user id $userId and transaction id $transactionId." }
                fundTransactionService.deleteTransaction(userId, transactionId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
        route("/funds/{fundId}/transactions") {
            get {
                val userId = call.userId()
                val fundId = call.parameters["fundId"]?.let(UUID::fromString) ?: error("Fund id is missing.")
                log.debug { "List all transactions by user id $userId and fund id $fundId." }
                val filter = call.parameters.fundTransactionsFilter()
                val fundTransactions = fundTransactionService.listTransactions(userId, fundId, filter)
                call.respond(fundTransactions.toListTO(FundTransaction::toTO))
            }
        }
    }
}

fun Parameters.fundTransactionsFilter(): FundTransactionFilterTO =
    FundTransactionFilterTO(
        fromDate = this["fromDate"]?.let { LocalDate.parse(it) },
        toDate = this["toDate"]?.let { LocalDate.parse(it) }
    )
