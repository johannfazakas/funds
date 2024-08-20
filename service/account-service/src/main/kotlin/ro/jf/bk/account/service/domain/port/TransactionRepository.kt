package ro.jf.bk.account.service.domain.port

import ro.jf.bk.account.service.domain.command.CreateTransactionCommand
import ro.jf.bk.account.service.domain.model.Transaction
import java.util.*

interface TransactionRepository {
    suspend fun list(userId: UUID): List<Transaction>
    suspend fun findById(userId: UUID, transactionId: UUID): Transaction?
    suspend fun save(command: CreateTransactionCommand): Transaction
    suspend fun deleteByUserId(userId: UUID)
    suspend fun deleteById(userId: UUID, transactionId: UUID)
    suspend fun deleteAll()
}
