package ro.jf.funds.fund.api

import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.fund.api.model.CreateTransactionTO
import ro.jf.funds.fund.api.model.TransactionFilterTO
import ro.jf.funds.fund.api.model.TransactionTO
import java.util.*

interface TransactionApi {
    suspend fun createTransaction(userId: UUID, transaction: CreateTransactionTO): TransactionTO
    suspend fun listTransactions(
        userId: UUID,
        filter: TransactionFilterTO = TransactionFilterTO.empty(),
    ): ListTO<TransactionTO>

    suspend fun deleteTransaction(userId: UUID, transactionId: UUID)
}
