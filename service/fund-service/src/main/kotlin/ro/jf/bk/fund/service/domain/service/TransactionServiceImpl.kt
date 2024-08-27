package ro.jf.bk.fund.service.domain.service

import ro.jf.bk.fund.service.domain.command.CreateTransactionCommand
import ro.jf.bk.fund.service.domain.model.Transaction
import ro.jf.bk.fund.service.domain.port.TransactionRepository
import ro.jf.bk.fund.service.domain.port.TransactionService
import java.util.*

class TransactionServiceImpl(
    private val transactionRepository: TransactionRepository
) : TransactionService {
    override suspend fun listTransactions(userId: UUID): List<Transaction> {
        return transactionRepository.listTransactions(userId)
    }

    override suspend fun createTransaction(command: CreateTransactionCommand): Transaction {
        TODO("Not yet implemented")
    }

    override suspend fun deleteTransaction(userId: UUID, transactionId: UUID) {
        return transactionRepository.deleteTransaction(userId, transactionId)
    }
}
