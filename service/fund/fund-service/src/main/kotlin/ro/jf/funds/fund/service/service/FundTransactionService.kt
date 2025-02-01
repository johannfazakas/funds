package ro.jf.funds.fund.service.service

import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.service.domain.FundServiceException
import ro.jf.funds.fund.service.domain.FundTransaction
import java.util.*

class FundTransactionService(
    private val fundService: FundService,
    private val accountTransactionAdapter: AccountTransactionAdapter,
) {
    suspend fun listTransactions(userId: UUID): List<FundTransaction> {
        return accountTransactionAdapter.listTransactions(userId)
    }

    suspend fun listTransactions(userId: UUID, fundId: UUID): List<FundTransaction> {
        return accountTransactionAdapter.listTransactions(userId, fundId)
    }

    suspend fun createTransaction(userId: UUID, request: CreateFundTransactionTO): FundTransaction {
        validateTransactionFunds(userId, listOf(request))
        return accountTransactionAdapter.createTransaction(userId, request)
    }

    suspend fun createTransactions(userId: UUID, correlationId: UUID, request: CreateFundTransactionsTO) {
        validateTransactionFunds(userId, request.transactions)
        return accountTransactionAdapter.createTransactions(userId, correlationId, request)
    }

    suspend fun deleteTransaction(userId: UUID, transactionId: UUID) {
        return accountTransactionAdapter.deleteTransaction(userId, transactionId)
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
