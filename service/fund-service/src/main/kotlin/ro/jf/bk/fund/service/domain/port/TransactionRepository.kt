package ro.jf.bk.fund.service.domain.port

import ro.jf.bk.fund.service.domain.command.CreateTransactionCommand
import ro.jf.bk.fund.service.domain.model.Transaction
import java.util.*

interface TransactionRepository {
    suspend fun listTransactions(userId: UUID): List<Transaction>
    suspend fun createTransaction(command: CreateTransactionCommand): Transaction
    suspend fun deleteTransaction(userId: UUID, transactionId: UUID)
}