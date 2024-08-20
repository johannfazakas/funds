package ro.jf.bk.fund.service.adapter.web

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.bk.commons.model.toListTO
import ro.jf.bk.commons.service.routing.userId
import ro.jf.bk.fund.service.adapter.mapper.toTO
import ro.jf.bk.fund.service.domain.model.Transaction
import ro.jf.bk.fund.service.domain.port.TransactionService

private val log = logger { }

fun Routing.transactionApiRouting(transactionService: TransactionService) {
    route("/bk-api/fund/v1/transactions") {
        get {
            val userId = call.userId()
            log.debug { "List all transactions by user id $userId." }
            val funds = transactionService.listTransactions(userId)
            call.respond(funds.toListTO(Transaction::toTO))
        }
        post {
            TODO()
        }
        delete("/{transactionId}") {
            TODO()
        }
    }
}