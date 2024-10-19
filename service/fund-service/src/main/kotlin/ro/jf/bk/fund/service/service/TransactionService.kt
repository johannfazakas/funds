package ro.jf.bk.fund.service.service

import ro.jf.bk.fund.api.model.CreateTransactionTO
import ro.jf.bk.fund.service.domain.Transaction
import java.util.*

class TransactionService(
    private val accountTransactionSdkAdapter: AccountTransactionSdkAdapter
) {
    suspend fun listTransactions(userId: UUID): List<Transaction> {
        return accountTransactionSdkAdapter.listTransactions(userId)
    }

    suspend fun createTransaction(command: CreateTransactionTO): Transaction {
        TODO("Not yet implemented")
    }

    suspend fun deleteTransaction(userId: UUID, transactionId: UUID) {
        return accountTransactionSdkAdapter.deleteTransaction(userId, transactionId)
    }
}
