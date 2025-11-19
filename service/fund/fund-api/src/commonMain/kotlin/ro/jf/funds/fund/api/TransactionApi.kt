package ro.jf.funds.fund.api

import com.benasher44.uuid.Uuid
import ro.jf.funds.commons.api.model.ListTO
import ro.jf.funds.fund.api.model.CreateTransactionTO
import ro.jf.funds.fund.api.model.TransactionFilterTO
import ro.jf.funds.fund.api.model.TransactionTO

interface TransactionApi {
    suspend fun createTransaction(userId: Uuid, transaction: CreateTransactionTO): TransactionTO
    suspend fun listTransactions(
        userId: Uuid,
        filter: TransactionFilterTO = TransactionFilterTO.empty(),
    ): ListTO<TransactionTO>

    suspend fun deleteTransaction(userId: Uuid, transactionId: Uuid)
}
