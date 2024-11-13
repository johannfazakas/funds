package ro.jf.funds.fund.service.service.event

import mu.KotlinLogging.logger
import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.event.Handler
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.service.service.FundTransactionService

private val log = logger { }

class CreateFundTransactionsRequestHandler(
    private val fundTransactionService: FundTransactionService
) : Handler<CreateFundTransactionsTO> {
    override suspend fun handle(event: Event<CreateFundTransactionsTO>) {
        log.info { "Received create fund transactions request $event" }
        val correlationId = event.correlationId ?: error("Correlation id is required")
        fundTransactionService.createTransactions(event.userId, correlationId, event.payload)
    }
}
