package ro.jf.funds.fund.service.service.event

import mu.KotlinLogging.logger
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.model.GenericResponse

private val log = logger { }

class CreateAccountTransactionsResponseHandler(
    private val createFundTransactionsResponseProducer: Producer<GenericResponse>
) : Handler<GenericResponse> {
    override suspend fun handle(event: Event<GenericResponse>) {
        log.info { "Received create account transactions response $event" }
        val response = Event(event.userId, event.payload, event.correlationId, event.userId.toString())
        createFundTransactionsResponseProducer.send(response)
    }
}
