package ro.jf.funds.account.service.service.event

import mu.KotlinLogging.logger
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.service.config.toError
import ro.jf.funds.account.service.service.AccountTransactionService
import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.event.Handler
import ro.jf.funds.commons.event.Producer
import ro.jf.funds.commons.model.GenericResponse

private val log = logger { }

class CreateAccountTransactionsRequestHandler(
    private val accountTransactionService: AccountTransactionService,
    private val createAccountTransactionsResponseProducer: Producer<GenericResponse>
) : Handler<CreateAccountTransactionsTO> {
    override suspend fun handle(event: Event<CreateAccountTransactionsTO>) {
        log.info { "Received create account transactions request $event" }
        try {
            accountTransactionService.createTransactions(event.userId, event.payload)
            createAccountTransactionsResponseProducer
                .send(Event(event.userId, GenericResponse.Success, event.correlationId))
        } catch (e: Exception) {
            log.error(e) { "Error creating account transactions" }
            createAccountTransactionsResponseProducer
                .send(Event(event.userId, GenericResponse.Error(e.toError()), event.correlationId))
        }
    }
}
