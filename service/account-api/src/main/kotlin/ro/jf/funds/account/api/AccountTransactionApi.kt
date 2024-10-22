package ro.jf.funds.account.api

import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.api.model.AccountTransactionTO
import java.util.*

interface AccountTransactionApi {
    suspend fun createTransaction(userId: UUID, request: CreateAccountTransactionTO): AccountTransactionTO
    suspend fun listTransactions(userId: UUID): List<AccountTransactionTO>
    suspend fun deleteTransaction(userId: UUID, transactionId: UUID)
}
