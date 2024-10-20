package ro.jf.bk.fund.service.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.bk.commons.model.toListTO
import ro.jf.bk.commons.service.routing.userId
import ro.jf.bk.fund.api.model.CreateFundTransactionTO
import ro.jf.bk.fund.service.domain.FundTransaction
import ro.jf.bk.fund.service.mapper.toTO
import ro.jf.bk.fund.service.service.FundTransactionService
import java.util.*

private val log = logger { }

fun Routing.fundTransactionApiRouting(fundTransactionService: FundTransactionService) {
    route("/bk-api/fund/v1/transactions") {
        get {
            val userId = call.userId()
            log.debug { "List all transactions by user id $userId." }
            val funds = fundTransactionService.listTransactions(userId)
            call.respond(funds.toListTO(FundTransaction::toTO))
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
}