package ro.jf.bk.account.service.domain.service

import ro.jf.bk.account.service.domain.model.Transaction
import ro.jf.bk.account.service.domain.port.TransactionRepository
import ro.jf.bk.account.service.domain.port.TransactionService
import java.util.*

class TransactionServiceImpl(private val transactionRepository: TransactionRepository) : TransactionService {
    override suspend fun listTransactions(userId: UUID): List<Transaction> {
        return transactionRepository.list(userId)
    }

    override suspend fun deleteTransaction(userId: UUID, transactionId: UUID) {
        transactionRepository.deleteById(userId, transactionId)
    }
}
