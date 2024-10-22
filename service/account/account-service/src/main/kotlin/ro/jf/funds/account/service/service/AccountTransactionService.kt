package ro.jf.funds.account.service.service

import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.service.domain.AccountTransaction
import ro.jf.funds.account.service.persistence.AccountTransactionRepository
import java.util.*

class AccountTransactionService(private val transactionRepository: AccountTransactionRepository) {
    suspend fun createTransaction(userId: UUID, request: CreateAccountTransactionTO): AccountTransaction {
        return transactionRepository.save(userId, request)
    }

    suspend fun listTransactions(userId: UUID): List<AccountTransaction> {
        return transactionRepository.list(userId)
    }

    suspend fun deleteTransaction(userId: UUID, transactionId: UUID) {
        transactionRepository.deleteById(userId, transactionId)
    }
}
