package ro.jf.funds.fund.service.service

import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.service.domain.FundTransaction
import java.util.*

class FundTransactionService(
    private val accountTransactionAdapter: AccountTransactionAdapter
) {
    suspend fun listTransactions(userId: UUID): List<FundTransaction> {
        return accountTransactionAdapter.listTransactions(userId)
    }

    suspend fun createTransaction(userId: UUID, request: CreateFundTransactionTO): FundTransaction {
        // TODO(Johann) should validate fund existence here? otherwise where?
        return accountTransactionAdapter.createTransaction(userId, request)
    }

    suspend fun createTransactions(userId: UUID, request: CreateFundTransactionsTO): List<FundTransaction> {
        // TODO(Johann) should validate fund existence here? otherwise where?
        return accountTransactionAdapter.createTransactions(userId, request)
    }

    suspend fun deleteTransaction(userId: UUID, transactionId: UUID) {
        return accountTransactionAdapter.deleteTransaction(userId, transactionId)
    }
}
