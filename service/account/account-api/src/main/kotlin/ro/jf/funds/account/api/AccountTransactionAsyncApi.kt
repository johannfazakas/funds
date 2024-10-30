package ro.jf.funds.account.api

import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import java.util.*

interface AccountTransactionAsyncApi {
    suspend fun createTransactions(userId: UUID, request: CreateAccountTransactionsTO)
}
