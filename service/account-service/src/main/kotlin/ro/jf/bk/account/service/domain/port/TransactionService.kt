package ro.jf.bk.account.service.domain.port

import ro.jf.bk.account.service.domain.model.Transaction
import java.util.*

interface TransactionService {
    suspend fun listTransactions(userId: UUID): List<Transaction>
}