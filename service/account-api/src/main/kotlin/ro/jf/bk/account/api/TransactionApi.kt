package ro.jf.bk.account.api

import ro.jf.bk.account.api.model.TransactionTO
import java.util.*

interface TransactionApi {
    suspend fun listTransactions(userId: UUID): List<TransactionTO>
    suspend fun deleteTransaction(userId: UUID, transactionId: UUID)
}
