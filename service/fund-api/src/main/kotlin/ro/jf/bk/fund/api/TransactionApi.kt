package ro.jf.bk.fund.api

import ro.jf.bk.fund.api.model.TransactionTO
import java.util.*

interface TransactionApi {
    suspend fun listTransactions(userId: UUID): List<TransactionTO>
    suspend fun deleteTransaction(userId: UUID, transactionId: UUID)
}