package ro.jf.funds.fund.service.service

import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.api.model.FundTransactionFilterTO
import ro.jf.funds.fund.service.domain.FundServiceException
import ro.jf.funds.fund.service.domain.FundTransaction
import java.util.*

class FundTransactionService(
    private val fundService: FundService,
    private val accountTransactionAdapter: AccountTransactionAdapter,
) {
    suspend fun listTransactions(
        userId: UUID,
        filter: FundTransactionFilterTO = FundTransactionFilterTO.empty(),
    ): List<FundTransaction> = withSuspendingSpan {
        accountTransactionAdapter.listTransactions(userId, filter = filter)
    }

    suspend fun listTransactions(
        userId: UUID,
        fundId: UUID,
        filter: FundTransactionFilterTO = FundTransactionFilterTO.empty(),
    ): List<FundTransaction> = withSuspendingSpan {
        accountTransactionAdapter.listTransactions(userId, fundId, filter)
    }

    suspend fun createTransaction(userId: UUID, request: CreateFundTransactionTO): FundTransaction = withSuspendingSpan {
        validateTransactionFunds(userId, listOf(request))
        accountTransactionAdapter.createTransaction(userId, request)
    }

    suspend fun createTransactions(userId: UUID, correlationId: UUID, request: CreateFundTransactionsTO) = withSuspendingSpan {
        validateTransactionFunds(userId, request.transactions)
        accountTransactionAdapter.createTransactions(userId, correlationId, request)
    }

    suspend fun deleteTransaction(userId: UUID, transactionId: UUID) = withSuspendingSpan {
        accountTransactionAdapter.deleteTransaction(userId, transactionId)
    }

    private suspend fun validateTransactionFunds(userId: UUID, requests: List<CreateFundTransactionTO>) {
        requests
            .asSequence()
            .flatMap { it.records }
            .map { it.fundId }
            .toSet()
            .forEach {
                fundService.findById(userId, it) ?: throw FundServiceException.TransactionFundNotFound(it)
            }
    }
}
