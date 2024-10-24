package ro.jf.funds.account.api

import ro.jf.funds.account.api.model.AccountTransactionTO
import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.commons.model.ListTO
import java.util.*

interface AccountTransactionApi {
    suspend fun createTransaction(userId: UUID, request: CreateAccountTransactionTO): AccountTransactionTO
    suspend fun createTransactions(
        userId: UUID,
        request: CreateAccountTransactionsTO
    ): ListTO<AccountTransactionTO>

    suspend fun listTransactions(userId: UUID): ListTO<AccountTransactionTO>
    suspend fun deleteTransaction(userId: UUID, transactionId: UUID)
}
