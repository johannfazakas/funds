package ro.jf.funds.fund.service.service.event

import mu.KotlinLogging.logger
import ro.jf.funds.commons.event.RequestHandler
import ro.jf.funds.commons.event.RpcRequest
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.service.service.FundTransactionService

private val log = logger { }

class CreateFundTransactionsRequestHandler(
    private val fundTransactionService: FundTransactionService
) : RequestHandler<CreateFundTransactionsTO>() {
    override suspend fun handle(request: RpcRequest<CreateFundTransactionsTO>) {
        log.info { "Received create fund transactions request $request" }
        fundTransactionService.createTransactions(request.userId, request.correlationId, request.payload)
    }
}
