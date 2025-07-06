package ro.jf.funds.fund.service.service.event

import mu.KotlinLogging.logger
import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.event.EventHandler
import ro.jf.funds.commons.event.Producer
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan

private val log = logger { }

class CreateAccountTransactionsResponseHandler(
    private val createFundTransactionsResponseProducer: Producer<GenericResponse>,
) : EventHandler<GenericResponse> {
    override suspend fun handle(event: Event<GenericResponse>): Unit = withSuspendingSpan {
        log.info { "Received create account transactions response $event" }
        val response = Event(event.userId, event.payload, event.correlationId, event.userId.toString())
        createFundTransactionsResponseProducer.send(response)
    }
}
