package ro.jf.funds.account.service.service.event

import mu.KotlinLogging.logger
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.service.service.AccountTransactionService
import ro.jf.funds.commons.event.RequestHandler
import ro.jf.funds.commons.event.ResponseProducer
import ro.jf.funds.commons.event.RpcRequest
import ro.jf.funds.commons.model.GenericResponse

private val log = logger { }

class CreateAccountTransactionsRequestHandler(
    private val accountTransactionService: AccountTransactionService,
    private val createAccountTransactionsResponseProducer: ResponseProducer<GenericResponse>
) : RequestHandler<CreateAccountTransactionsTO>() {
    override suspend fun handle(request: RpcRequest<CreateAccountTransactionsTO>) {
        log.info { "Received create account transactions request $request" }
        accountTransactionService.createTransactions(request.userId, request.payload)
        createAccountTransactionsResponseProducer.send(request.userId, request.correlationId, GenericResponse.Success)
    }
}
