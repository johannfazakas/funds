package ro.jf.funds.fund.service.service.event

import mu.KotlinLogging.logger
import ro.jf.funds.commons.event.ResponseHandler
import ro.jf.funds.commons.event.ResponseProducer
import ro.jf.funds.commons.event.RpcResponse
import ro.jf.funds.commons.model.GenericResponse

private val log = logger { }

class CreateAccountTransactionsResponseHandler(
    private val createFundTransactionsResponseProducer: ResponseProducer<GenericResponse>
) : ResponseHandler<GenericResponse>() {
    override suspend fun handle(event: RpcResponse<GenericResponse>) {
        log.info { "Received create account transactions response $event" }
        createFundTransactionsResponseProducer.send(event.userId, event.correlationId, event.payload)
    }
}
