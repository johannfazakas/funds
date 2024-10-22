package ro.jf.funds.fund.api

import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.FundTransactionTO
import java.util.*

interface FundTransactionApi {
    suspend fun createTransaction(userId: UUID, transaction: CreateFundTransactionTO): FundTransactionTO
    suspend fun listTransactions(userId: UUID): List<FundTransactionTO>
    suspend fun deleteTransaction(userId: UUID, transactionId: UUID)
}
