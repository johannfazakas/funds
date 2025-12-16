package ro.jf.funds.fund.service.service.event

import mu.KotlinLogging.logger
import ro.jf.funds.platform.jvm.event.Event
import ro.jf.funds.platform.jvm.event.EventHandler
import ro.jf.funds.platform.jvm.event.Producer
import ro.jf.funds.platform.jvm.model.GenericResponse
import ro.jf.funds.fund.api.model.CreateTransactionsTO
import ro.jf.funds.fund.service.config.toError
import ro.jf.funds.fund.service.service.TransactionService

private val log = logger { }

class CreateTransactionsRequestHandler(
    private val transactionService: TransactionService,
    private val createTransactionsResponseProducer: Producer<GenericResponse>,
) : EventHandler<CreateTransactionsTO> {
    override suspend fun handle(event: Event<CreateTransactionsTO>) {
        log.info { "Received create fund transactions request. userId = ${event.userId}, payload size = ${event.payload.transactions.size}" }
        try {
            transactionService.createTransactions(event.userId, event.payload)
            createTransactionsResponseProducer
                .send(Event(event.userId, GenericResponse.Success, event.correlationId))
        } catch (e: Exception) {
            log.error(e) { "Error creating fund transactions" }
            createTransactionsResponseProducer
                .send(Event(event.userId, GenericResponse.Error(e.toError()), event.correlationId))
        }
    }
}
