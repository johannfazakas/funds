package ro.jf.funds.fund.api

import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.api.model.FundTransactionTO
import java.util.*

interface FundTransactionApi {
    suspend fun createTransaction(userId: UUID, transaction: CreateFundTransactionTO): FundTransactionTO
    suspend fun createTransactions(userId: UUID, transactions: CreateFundTransactionsTO): ListTO<FundTransactionTO>
    suspend fun listTransactions(userId: UUID): ListTO<FundTransactionTO>
    suspend fun deleteTransaction(userId: UUID, transactionId: UUID)
}
